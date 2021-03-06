package uk.gov.dhsc.htbhf.claimant.message.payload;

import lombok.Builder;
import lombok.Value;
import uk.gov.dhsc.htbhf.claimant.model.UpdatableClaimantField;
import uk.gov.dhsc.htbhf.claimant.reporting.ClaimAction;
import uk.gov.dhsc.htbhf.eligibility.model.CombinedIdentityAndEligibilityResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class ReportClaimMessagePayload implements MessagePayload {
    private UUID claimId;
    private CombinedIdentityAndEligibilityResponse identityAndEligibilityResponse;
    private ClaimAction claimAction;
    private LocalDateTime timestamp;
    private List<UpdatableClaimantField> updatedClaimantFields;
}
