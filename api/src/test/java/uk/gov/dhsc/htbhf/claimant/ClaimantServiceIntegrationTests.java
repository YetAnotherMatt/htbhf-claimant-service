package uk.gov.dhsc.htbhf.claimant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.ResponseEntity;
import uk.gov.dhsc.htbhf.claimant.entity.Claim;
import uk.gov.dhsc.htbhf.claimant.entity.Claimant;
import uk.gov.dhsc.htbhf.claimant.model.AddressDTO;
import uk.gov.dhsc.htbhf.claimant.model.ClaimDTO;
import uk.gov.dhsc.htbhf.claimant.model.ClaimResultDTO;
import uk.gov.dhsc.htbhf.claimant.model.ClaimStatus;
import uk.gov.dhsc.htbhf.claimant.model.ClaimantDTO;
import uk.gov.dhsc.htbhf.claimant.repository.ClaimRepository;
import uk.gov.dhsc.htbhf.claimant.testsupport.RepositoryMediator;
import uk.gov.dhsc.htbhf.eligibility.model.CombinedIdentityAndEligibilityResponse;
import uk.gov.dhsc.htbhf.eligibility.model.EligibilityStatus;
import uk.gov.dhsc.htbhf.errorhandler.ErrorResponse;

import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static uk.gov.dhsc.htbhf.TestConstants.DWP_HOUSEHOLD_IDENTIFIER;
import static uk.gov.dhsc.htbhf.TestConstants.HMRC_HOUSEHOLD_IDENTIFIER;
import static uk.gov.dhsc.htbhf.TestConstants.HOMER_NINO;
import static uk.gov.dhsc.htbhf.assertions.IntegrationTestAssertions.assertInternalServerErrorResponse;
import static uk.gov.dhsc.htbhf.assertions.IntegrationTestAssertions.assertRequestCouldNotBeParsedErrorResponse;
import static uk.gov.dhsc.htbhf.assertions.IntegrationTestAssertions.assertValidationErrorInResponse;
import static uk.gov.dhsc.htbhf.claimant.ClaimantServiceAssertionUtils.assertClaimantMatchesClaimantDTO;
import static uk.gov.dhsc.htbhf.claimant.ClaimantServiceAssertionUtils.buildClaimRequestEntity;
import static uk.gov.dhsc.htbhf.claimant.testsupport.AddressDTOTestDataFactory.anAddressDTOWithLine1;
import static uk.gov.dhsc.htbhf.claimant.testsupport.AddressDTOTestDataFactory.anAddressDTOWithPostcode;
import static uk.gov.dhsc.htbhf.claimant.testsupport.ClaimDTOTestDataFactory.aClaimDTOWithClaimant;
import static uk.gov.dhsc.htbhf.claimant.testsupport.ClaimDTOTestDataFactory.aClaimDTOWithCounty;
import static uk.gov.dhsc.htbhf.claimant.testsupport.ClaimDTOTestDataFactory.aValidClaimDTO;
import static uk.gov.dhsc.htbhf.claimant.testsupport.ClaimDTOTestDataFactory.aValidClaimDTOWithNoNullFields;
import static uk.gov.dhsc.htbhf.claimant.testsupport.ClaimTestDataFactory.aValidClaimBuilder;
import static uk.gov.dhsc.htbhf.claimant.testsupport.ClaimantDTOTestDataFactory.aClaimantDTOWithAddress;
import static uk.gov.dhsc.htbhf.claimant.testsupport.ClaimantDTOTestDataFactory.aClaimantDTOWithNino;
import static uk.gov.dhsc.htbhf.claimant.testsupport.ClaimantDTOTestDataFactory.aClaimantDTOWithPhoneNumber;
import static uk.gov.dhsc.htbhf.claimant.testsupport.ClaimantTestDataFactory.aClaimantWithNino;
import static uk.gov.dhsc.htbhf.claimant.testsupport.ClaimantTestDataFactory.aValidClaimantInSameHouseholdBuilder;
import static uk.gov.dhsc.htbhf.claimant.testsupport.VerificationResultTestDataFactory.anAllMatchedVerificationResult;
import static uk.gov.dhsc.htbhf.claimant.testsupport.VoucherEntitlementDTOTestDataFactory.aValidVoucherEntitlementDTO;
import static uk.gov.dhsc.htbhf.eligibility.model.EligibilityStatus.DUPLICATE;
import static uk.gov.dhsc.htbhf.eligibility.model.EligibilityStatus.ELIGIBLE;
import static uk.gov.dhsc.htbhf.eligibility.model.EligibilityStatus.ERROR;
import static uk.gov.dhsc.htbhf.eligibility.model.testhelper.CombinedIdAndEligibilityResponseTestDataFactory.anIdMatchedEligibilityConfirmedUCResponseWithAllMatches;
import static uk.gov.dhsc.htbhf.eligibility.model.testhelper.CombinedIdAndEligibilityResponseTestDataFactory.anIdMatchedEligibilityConfirmedUCResponseWithAllMatchesAndDwpHouseIdentifier;
import static uk.gov.dhsc.htbhf.eligibility.model.testhelper.CombinedIdAndEligibilityResponseTestDataFactory.anIdMatchedEligibilityConfirmedUCResponseWithAllMatchesAndHmrcHouseIdentifier;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureEmbeddedDatabase
@AutoConfigureWireMock(port = 8100)
class ClaimantServiceIntegrationTests {

    private static final String ELIGIBILITY_SERVICE_URL = "/v2/eligibility";

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ClaimRepository claimRepository;

    @Autowired
    RepositoryMediator repositoryMediator;

    @AfterEach
    void cleanup() {
        repositoryMediator.deleteAllEntities();
        WireMock.reset();
    }

    @Test
    void shouldAcceptAndCreateANewValidClaimWithNoNullFields() throws JsonProcessingException {
        //Given
        ClaimDTO claim = aValidClaimDTOWithNoNullFields();
        CombinedIdentityAndEligibilityResponse identityAndEligibilityResponse = anIdMatchedEligibilityConfirmedUCResponseWithAllMatches();
        stubEligibilityServiceWithSuccessfulResponse(identityAndEligibilityResponse);
        //When
        ResponseEntity<ClaimResultDTO> response = restTemplate.exchange(buildClaimRequestEntity(claim), ClaimResultDTO.class);
        //Then
        assertThatClaimResultHasNewClaim(response);
        assertClaimPersistedSuccessfully(claim, ELIGIBLE);
        verifyPostToEligibilityService();
    }

    @Test
    void shouldIgnoreUnknownFieldsInValidClaimRequest() throws JsonProcessingException {
        //Given
        ClaimDTO claim = aValidClaimDTOWithNoNullFields();
        String webUiVersionProperty = "\"" + claim.getWebUIVersion() + "\",";
        String json = objectMapper.writeValueAsString(claim).replace(webUiVersionProperty, webUiVersionProperty + " \"foo\":\"bar\",");
        CombinedIdentityAndEligibilityResponse identityAndEligibilityResponse = anIdMatchedEligibilityConfirmedUCResponseWithAllMatches();
        stubEligibilityServiceWithSuccessfulResponse(identityAndEligibilityResponse);

        //When
        ResponseEntity<ClaimResultDTO> response = restTemplate.exchange(buildClaimRequestEntity(json), ClaimResultDTO.class);

        //Then
        assertThatClaimResultHasNewClaim(response);
        assertClaimPersistedSuccessfully(claim, ELIGIBLE);
        verifyPostToEligibilityService();
    }

    @ParameterizedTest
    @MethodSource("provideArgumentsForMissingAddressFields")
    void shouldAcceptAndCreateANewValidClaimWithMissingAddressFields(String county) throws JsonProcessingException {
        //Given
        ClaimDTO claim = aClaimDTOWithCounty(county);
        CombinedIdentityAndEligibilityResponse identityAndEligibilityResponse = anIdMatchedEligibilityConfirmedUCResponseWithAllMatches();
        stubEligibilityServiceWithSuccessfulResponse(identityAndEligibilityResponse);

        //When
        ResponseEntity<ClaimResultDTO> response = restTemplate.exchange(buildClaimRequestEntity(claim), ClaimResultDTO.class);

        //Then
        assertThatClaimResultHasNewClaim(response);
        assertClaimPersistedSuccessfully(claim, ELIGIBLE);
        verifyPostToEligibilityService();
    }

    private static Stream<Arguments> provideArgumentsForMissingAddressFields() {
        return Stream.of(
            Arguments.of(""),
            Arguments.of((Object) null)
        );
    }

    @Test
    void shouldFailWhenEligibilityServiceCallThrowsException() {
        //Given
        ClaimDTO claim = aValidClaimDTO();
        stubEligibilityServiceWithUnsuccessfulResponse();

        //When
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(buildClaimRequestEntity(claim), ErrorResponse.class);

        //Then
        assertInternalServerErrorResponse(response);
        assertClaimPersistedWithError(claim);
        verifyPostToEligibilityService();
    }

    @Test
    void shouldReturn404ErrorForNonExistentPath() {
        //When
        ResponseEntity<ErrorResponse> response = restTemplate.getForEntity("/missing-resource", ErrorResponse.class);
        //Then
        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void shouldReturnDuplicateStatusWhenEligibleClaimAlreadyExistsForDwpHouseholdIdentifier() throws JsonProcessingException {
        //Given
        ClaimDTO dto = aValidClaimDTO();
        Claimant claimant = aValidClaimantInSameHouseholdBuilder().build();
        Claim claim = aValidClaimBuilder()
                .dwpHouseholdIdentifier(DWP_HOUSEHOLD_IDENTIFIER)
                .claimant(claimant)
                .build();
        claimRepository.save(claim);

        CombinedIdentityAndEligibilityResponse identityAndEligibilityResponse
                = anIdMatchedEligibilityConfirmedUCResponseWithAllMatchesAndDwpHouseIdentifier(DWP_HOUSEHOLD_IDENTIFIER);
        stubEligibilityServiceWithSuccessfulResponse(identityAndEligibilityResponse);

        //When
        ResponseEntity<ClaimResultDTO> response = restTemplate.exchange(buildClaimRequestEntity(dto), ClaimResultDTO.class);

        //Then
        assertDuplicateResponse(response);
        verifyPostToEligibilityService();
    }

    @Test
    void shouldReturnDuplicateStatusWhenEligibleClaimAlreadyExistsForHmrcHouseholdIdentifier() throws JsonProcessingException {
        //Given
        ClaimDTO dto = aValidClaimDTO();
        Claimant claimant = aValidClaimantInSameHouseholdBuilder().build();
        Claim claim = aValidClaimBuilder()
                .hmrcHouseholdIdentifier(HMRC_HOUSEHOLD_IDENTIFIER)
                .claimant(claimant)
                .build();
        claimRepository.save(claim);

        CombinedIdentityAndEligibilityResponse identityAndEligibilityResponse
                = anIdMatchedEligibilityConfirmedUCResponseWithAllMatchesAndHmrcHouseIdentifier(HMRC_HOUSEHOLD_IDENTIFIER);
        stubEligibilityServiceWithSuccessfulResponse(identityAndEligibilityResponse);

        //When
        ResponseEntity<ClaimResultDTO> response = restTemplate.exchange(buildClaimRequestEntity(dto), ClaimResultDTO.class);

        //Then
        assertDuplicateResponse(response);
    }

    @Test
    void shouldReturnDuplicateStatusWhenEligibleClaimAlreadyExistsWithSameNino() {
        //Given
        ClaimDTO dto = aClaimDTOWithClaimant(aClaimantDTOWithNino(HOMER_NINO));
        Claimant claimant = aClaimantWithNino(HOMER_NINO);
        Claim claim = aValidClaimBuilder()
                .claimant(claimant)
                .build();
        claimRepository.save(claim);

        //When
        ResponseEntity<ClaimResultDTO> response = restTemplate.exchange(buildClaimRequestEntity(dto), ClaimResultDTO.class);

        //Then
        assertDuplicateResponse(response);
    }

    @Test
    void shouldFailWithClaimantValidationError() {
        //Given
        ClaimantDTO claimant = aClaimantDTOWithPhoneNumber(null);
        ClaimDTO claim = aClaimDTOWithClaimant(claimant);
        //When
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(buildClaimRequestEntity(claim), ErrorResponse.class);
        //Then
        assertValidationErrorInResponse(response, "claimant.phoneNumber", "must not be null");
    }

    @Test
    void shouldFailWithAddressValidationError() {
        //Given
        AddressDTO addressDTO = anAddressDTOWithLine1(null);
        ClaimantDTO claimant = aClaimantDTOWithAddress(addressDTO);
        ClaimDTO claim = aClaimDTOWithClaimant(claimant);
        //When
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(buildClaimRequestEntity(claim), ErrorResponse.class);
        //Then
        assertValidationErrorInResponse(response, "claimant.address.addressLine1", "must not be null");
    }

    @Test
    void shouldFailWithChannelIslandPostcode() {
        //Given
        AddressDTO addressDTO = anAddressDTOWithPostcode("GY11AA");
        ClaimantDTO claimant = aClaimantDTOWithAddress(addressDTO);
        ClaimDTO claim = aClaimDTOWithClaimant(claimant);
        //When
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(buildClaimRequestEntity(claim), ErrorResponse.class);
        //Then
        assertValidationErrorInResponse(response, "claimant.address.postcode", "postcodes in the Channel Islands or Isle of Man are not acceptable");
    }

    @ParameterizedTest(name = "Field {1} with invalid value {0} on an error response")
    @CsvSource({
            "29-11-1909, dateOfBirth, '29-11-1909' could not be parsed as a LocalDate, claimant.dateOfBirth",
            "1999/12/31, dateOfBirth, '1999/12/31' could not be parsed as a LocalDate, claimant.dateOfBirth",
            "Foo, dateOfBirth, 'Foo' could not be parsed as a LocalDate, claimant.dateOfBirth",
            "29-11-1909, expectedDeliveryDate, '29-11-1909' could not be parsed as a LocalDate, claimant.expectedDeliveryDate",
            "1999/12/31, expectedDeliveryDate, '1999/12/31' could not be parsed as a LocalDate, claimant.expectedDeliveryDate",
            "Foo, expectedDeliveryDate, 'Foo' could not be parsed as a LocalDate, claimant.expectedDeliveryDate"
    })
    void shouldFailWithInvalidDateFormatError(String dateString, String fieldName, String expectedErrorMessage, String expectedField)
            throws JsonProcessingException {
        //Given
        String claimWithInvalidDate = modifyFieldOnClaimantInJson(aValidClaimDTO(), fieldName, dateString);
        //When
        ResponseEntity<ErrorResponse> response
                = restTemplate.exchange(buildClaimRequestEntity(claimWithInvalidDate), ErrorResponse.class);
        //Then
        assertRequestCouldNotBeParsedErrorResponse(response, expectedField, expectedErrorMessage);
    }

    @Test
    void shouldReturnErrorGivenAnEmptyClaim() {
        //Given
        String claim = "{}";
        //When
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(buildClaimRequestEntity(claim), ErrorResponse.class);
        //Then
        assertValidationErrorInResponse(response, "claimant", "must not be null");
    }

    private void assertDuplicateResponse(ResponseEntity<ClaimResultDTO> response) {
        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getEligibilityStatus()).isEqualTo(DUPLICATE);
        assertThat(response.getBody().getClaimStatus()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(response.getBody().getVoucherEntitlement()).isNull();
    }

    private void assertThatClaimResultHasNewClaim(ResponseEntity<ClaimResultDTO> response) {
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getClaimStatus()).isEqualTo(ClaimStatus.NEW);
        assertThat(response.getBody().getEligibilityStatus()).isEqualTo(ELIGIBLE);
        assertThat(response.getBody().getVoucherEntitlement()).isEqualTo(aValidVoucherEntitlementDTO());
        assertThat(response.getBody().getVerificationResult()).isEqualTo(anAllMatchedVerificationResult());
    }

    private void assertClaimPersistedSuccessfully(ClaimDTO claimDTO,
                                                  EligibilityStatus eligibilityStatus) {
        Claim persistedClaim = assertClaimPersistedWithClaimStatus(claimDTO, eligibilityStatus);
        assertThat(persistedClaim.getDwpHouseholdIdentifier()).isEqualTo(DWP_HOUSEHOLD_IDENTIFIER);
        assertThat(persistedClaim.getHmrcHouseholdIdentifier()).isEqualTo(HMRC_HOUSEHOLD_IDENTIFIER);
    }

    private void assertClaimPersistedWithError(ClaimDTO claimDTO) {
        assertClaimPersistedWithClaimStatus(claimDTO, ERROR);
    }

    private Claim assertClaimPersistedWithClaimStatus(ClaimDTO claimDTO, EligibilityStatus eligibilityStatus) {
        Iterable<Claim> claims = claimRepository.findAll();
        assertThat(claims).hasSize(1);
        Claim persistedClaim = claims.iterator().next();
        assertClaimantMatchesClaimantDTO(claimDTO.getClaimant(), persistedClaim.getClaimant());
        assertThat(persistedClaim.getEligibilityStatus()).isEqualTo(eligibilityStatus);
        return persistedClaim;
    }


    private void stubEligibilityServiceWithSuccessfulResponse(CombinedIdentityAndEligibilityResponse identityAndEligibilityResponse)
            throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(identityAndEligibilityResponse);
        stubFor(post(urlEqualTo(ELIGIBILITY_SERVICE_URL)).willReturn(okJson(json)));
    }

    private void stubEligibilityServiceWithUnsuccessfulResponse() {
        stubFor(post(urlEqualTo(ELIGIBILITY_SERVICE_URL)).willReturn(serverError()));
    }

    private void verifyPostToEligibilityService() {
        verify(exactly(1), postRequestedFor(urlEqualTo(ELIGIBILITY_SERVICE_URL)));
    }

    private String modifyFieldOnClaimantInJson(Object originalValue, String fieldName, String newValue) throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(originalValue);
        JSONObject jsonObject = new JSONObject(json);
        jsonObject.getJSONObject("claimant").put(fieldName, newValue);
        return jsonObject.toString();
    }
}
