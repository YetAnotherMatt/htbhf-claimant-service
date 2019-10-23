package uk.gov.dhsc.htbhf.claimant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.dhsc.htbhf.claimant.entity.Claim;
import uk.gov.dhsc.htbhf.claimant.message.MessageQueueClient;
import uk.gov.dhsc.htbhf.claimant.message.payload.AdditionalPregnancyPaymentMessagePayload;
import uk.gov.dhsc.htbhf.claimant.message.payload.NewCardRequestMessagePayload;
import uk.gov.dhsc.htbhf.claimant.message.payload.ReportClaimMessagePayload;
import uk.gov.dhsc.htbhf.claimant.model.eligibility.EligibilityAndEntitlementDecision;
import uk.gov.dhsc.htbhf.claimant.reporting.ClaimAction;

import java.time.LocalDate;
import java.util.List;

import static uk.gov.dhsc.htbhf.claimant.message.MessagePayloadFactory.buildNewCardMessagePayload;
import static uk.gov.dhsc.htbhf.claimant.message.MessagePayloadFactory.buildReportClaimMessagePayload;
import static uk.gov.dhsc.htbhf.claimant.message.MessageType.ADDITIONAL_PREGNANCY_PAYMENT;
import static uk.gov.dhsc.htbhf.claimant.message.MessageType.CREATE_NEW_CARD;
import static uk.gov.dhsc.htbhf.claimant.message.MessageType.REPORT_CLAIM;

/**
 * Responsible for sending messages related to claims (new or updated).
 */
@Component
@RequiredArgsConstructor
public class ClaimMessageSender {

    private final MessageQueueClient messageQueueClient;

    public void sendReportClaimMessage(Claim claim, List<LocalDate> datesOfBirthOfChildren, ClaimAction claimAction) {
        ReportClaimMessagePayload payload = buildReportClaimMessagePayload(claim, datesOfBirthOfChildren, claimAction);
        messageQueueClient.sendMessage(payload, REPORT_CLAIM);
    }

    public void sendAdditionalPaymentMessage(Claim claim) {
        AdditionalPregnancyPaymentMessagePayload payload = AdditionalPregnancyPaymentMessagePayload.withClaimId(claim.getId());
        messageQueueClient.sendMessage(payload, ADDITIONAL_PREGNANCY_PAYMENT);
    }

    public void sendNewCardMessage(Claim claim, EligibilityAndEntitlementDecision decision) {
        NewCardRequestMessagePayload payload = buildNewCardMessagePayload(claim, decision.getVoucherEntitlement(), decision.getDateOfBirthOfChildren());
        messageQueueClient.sendMessage(payload, CREATE_NEW_CARD);
    }
}