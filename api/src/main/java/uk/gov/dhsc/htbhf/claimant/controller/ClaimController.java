package uk.gov.dhsc.htbhf.claimant.controller;

import io.swagger.annotations.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.dhsc.htbhf.claimant.converter.ClaimDTOToClaimConverter;
import uk.gov.dhsc.htbhf.claimant.entity.Claim;
import uk.gov.dhsc.htbhf.claimant.entity.Claimant;
import uk.gov.dhsc.htbhf.claimant.model.ClaimDTO;
import uk.gov.dhsc.htbhf.claimant.model.ClaimResponse;
import uk.gov.dhsc.htbhf.claimant.model.eligibility.EligibilityStatus;
import uk.gov.dhsc.htbhf.claimant.service.ClaimService;
import uk.gov.dhsc.htbhf.errorhandler.ErrorResponse;

import java.util.Map;
import javax.validation.Valid;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController()
@RequestMapping(value = "/v1/claims", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Api(description = "Endpoints for dealing with claims, e.g. persisting a claim.")
public class ClaimController {

    private final ClaimService claimService;
    private final ClaimDTOToClaimConverter converter;
    private final Map<EligibilityStatus, HttpStatus> statusMap = Map.of(
            EligibilityStatus.ELIGIBLE, HttpStatus.CREATED,
            EligibilityStatus.DUPLICATE, HttpStatus.OK
    );

    @PostMapping
    @ApiOperation("Persist a new claim.")
    @ApiResponses({@ApiResponse(code = 400, message = "Bad request", response = ErrorResponse.class)})
    public ResponseEntity<ClaimResponse> newClaim(@RequestBody @Valid @ApiParam("The claim to persist") ClaimDTO claimDTO) {
        log.debug("Received claim");
        Claim claim = converter.convert(claimDTO);
        Claimant claimant = claimService.createClaim(claim);

        return createResponseFromClaimant(claimant);
    }

    private ResponseEntity createResponseFromClaimant(Claimant claimant) {
        EligibilityStatus eligibilityStatus = claimant.getEligibilityStatus();
        HttpStatus statusCode = statusMap.get(eligibilityStatus);
        ClaimResponse body = ClaimResponse.builder().eligibilityStatus(eligibilityStatus).build();
        return new ResponseEntity(body, statusCode);
    }
}
