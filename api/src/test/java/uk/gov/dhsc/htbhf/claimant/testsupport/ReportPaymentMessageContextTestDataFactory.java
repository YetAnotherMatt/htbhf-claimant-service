package uk.gov.dhsc.htbhf.claimant.testsupport;

import uk.gov.dhsc.htbhf.claimant.entity.Claim;
import uk.gov.dhsc.htbhf.claimant.entity.PaymentCycle;
import uk.gov.dhsc.htbhf.claimant.message.context.ReportPaymentMessageContext;

import java.time.LocalDateTime;

import static uk.gov.dhsc.htbhf.TestConstants.SINGLE_THREE_YEAR_OLD;
import static uk.gov.dhsc.htbhf.claimant.reporting.PaymentAction.INITIAL_PAYMENT;
import static uk.gov.dhsc.htbhf.claimant.testsupport.ClaimTestDataFactory.aValidClaim;
import static uk.gov.dhsc.htbhf.claimant.testsupport.PaymentCycleTestDataFactory.aPaymentCycleWithClaim;
import static uk.gov.dhsc.htbhf.eligibility.model.testhelper.CombinedIdAndEligibilityResponseTestDataFactory.anIdMatchedEligibilityConfirmedUCResponseWithAllMatches;

public class ReportPaymentMessageContextTestDataFactory {

    public static ReportPaymentMessageContext aValidReportPaymentMessageContext() {
        Claim claim = aValidClaim();
        PaymentCycle paymentCycle = aPaymentCycleWithClaim(claim);
        return ReportPaymentMessageContext.builder()
                .claim(claim)
                .paymentCycle(paymentCycle)
                .paymentAction(INITIAL_PAYMENT)
                .timestamp(LocalDateTime.now())
                .identityAndEligibilityResponse(anIdMatchedEligibilityConfirmedUCResponseWithAllMatches(SINGLE_THREE_YEAR_OLD))
                .build();
    }

    public static ReportPaymentMessageContext aReportPaymentMessageContextWithClaim(Claim claim) {
        PaymentCycle paymentCycle = aPaymentCycleWithClaim(claim);
        return ReportPaymentMessageContext.builder()
                .claim(claim)
                .paymentCycle(paymentCycle)
                .paymentAction(INITIAL_PAYMENT)
                .timestamp(LocalDateTime.now())
                .identityAndEligibilityResponse(anIdMatchedEligibilityConfirmedUCResponseWithAllMatches(SINGLE_THREE_YEAR_OLD))
                .build();
    }
}
