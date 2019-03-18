package uk.gov.dhsc.htbhf.claimant.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import uk.gov.dhsc.htbhf.claimant.entity.Claimant;
import uk.gov.dhsc.htbhf.claimant.model.eligibility.EligibilityResponse;
import uk.gov.dhsc.htbhf.claimant.model.eligibility.PersonDTO;

/**
 * Client for calling the Eligibility Service using the RestTemplate defined in
 * {@link uk.gov.dhsc.htbhf.requestcontext.RequestContextConfiguration}.
 */
@Component
public class EligibilityClient {

    public static final String ELIGIBILITY_ENDPOINT = "/v1/eligibility";
    private final RestTemplate restTemplateWithIdHeaders;
    private final ClaimantToPersonDTOConverter claimantToPersonDTOConverter;
    private final String eligibilityUri;

    public EligibilityClient(@Value("${eligibility.base-uri}") String baseUri,
                             RestTemplate restTemplateWithIdHeaders,
                             ClaimantToPersonDTOConverter claimantToPersonDTOConverter) {
        this.eligibilityUri = baseUri + ELIGIBILITY_ENDPOINT;
        this.restTemplateWithIdHeaders = restTemplateWithIdHeaders;
        this.claimantToPersonDTOConverter = claimantToPersonDTOConverter;
    }

    public EligibilityResponse checkEligibility(Claimant claimant) {
        PersonDTO person = claimantToPersonDTOConverter.convert(claimant);
        ResponseEntity<EligibilityResponse> response = restTemplateWithIdHeaders.postForEntity(
                eligibilityUri,
                person,
                EligibilityResponse.class
        );
        //TODO Log and throw an Exception if not a 200.
        return response.getBody();
    }
}
