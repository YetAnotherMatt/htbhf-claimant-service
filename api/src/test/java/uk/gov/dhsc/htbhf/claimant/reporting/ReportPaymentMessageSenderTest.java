package uk.gov.dhsc.htbhf.claimant.reporting;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.dhsc.htbhf.claimant.entitlement.PaymentCycleVoucherEntitlement;
import uk.gov.dhsc.htbhf.claimant.entity.Claim;
import uk.gov.dhsc.htbhf.claimant.entity.PaymentCycle;
import uk.gov.dhsc.htbhf.claimant.message.MessageQueueClient;
import uk.gov.dhsc.htbhf.claimant.message.payload.ReportPaymentMessagePayload;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static uk.gov.dhsc.htbhf.claimant.message.MessageType.REPORT_PAYMENT;
import static uk.gov.dhsc.htbhf.claimant.reporting.PaymentAction.INITIAL_PAYMENT;
import static uk.gov.dhsc.htbhf.claimant.reporting.PaymentAction.SCHEDULED_PAYMENT;
import static uk.gov.dhsc.htbhf.claimant.reporting.PaymentAction.TOP_UP_PAYMENT;
import static uk.gov.dhsc.htbhf.claimant.testsupport.ClaimTestDataFactory.aValidClaim;
import static uk.gov.dhsc.htbhf.claimant.testsupport.PaymentCycleTestDataFactory.aPaymentCycleWithBackdatedVouchersOnly;
import static uk.gov.dhsc.htbhf.claimant.testsupport.PaymentCycleTestDataFactory.aPaymentCycleWithClaim;

@ExtendWith(MockitoExtension.class)
class ReportPaymentMessageSenderTest {

    @Mock
    private MessageQueueClient messageQueueClient;

    @InjectMocks
    private ReportPaymentMessageSender reportPaymentMessageSender;

    @Test
    void shouldReportTopUpPayment() {
        Claim claim = aValidClaim();
        PaymentCycle paymentCycle = aPaymentCycleWithClaim(claim);
        int paymentForPregnancy = 100;
        LocalDateTime testStart = LocalDateTime.now();

        reportPaymentMessageSender.sendReportPregnancyTopUpPaymentMessage(claim, paymentCycle, paymentForPregnancy);

        ArgumentCaptor<ReportPaymentMessagePayload> argumentCaptor = ArgumentCaptor.forClass(ReportPaymentMessagePayload.class);
        verify(messageQueueClient).sendMessage(argumentCaptor.capture(), eq(REPORT_PAYMENT));
        ReportPaymentMessagePayload payload = argumentCaptor.getValue();
        assertThat(payload.getTimestamp()).isAfterOrEqualTo(testStart);
        assertThat(payload.getClaimId()).isEqualTo(claim.getId());
        assertThat(payload.getPaymentCycleId()).isEqualTo(paymentCycle.getId());
        assertThat(payload.getIdentityAndEligibilityResponse()).isEqualTo(paymentCycle.getIdentityAndEligibilityResponse());
        assertThat(payload.getPaymentAction()).isEqualTo(TOP_UP_PAYMENT);
        assertThat(payload.getPaymentForChildrenUnderOne()).isZero();
        assertThat(payload.getPaymentForChildrenBetweenOneAndFour()).isZero();
        assertThat(payload.getPaymentForPregnancy()).isEqualTo(paymentForPregnancy);
    }

    @Test
    void shouldReportInitialPayment() {
        Claim claim = aValidClaim();
        PaymentCycle paymentCycle = aPaymentCycleWithClaim(claim);
        LocalDateTime testStart = LocalDateTime.now();

        reportPaymentMessageSender.sendReportPaymentMessage(claim, paymentCycle, INITIAL_PAYMENT);

        verifyNonTopUpPaymentMessageSent(claim, paymentCycle, testStart, INITIAL_PAYMENT);
    }

    @Test
    void shouldReportScheduledPayment() {
        Claim claim = aValidClaim();
        PaymentCycle paymentCycle = aPaymentCycleWithClaim(claim);
        LocalDateTime testStart = LocalDateTime.now();

        reportPaymentMessageSender.sendReportPaymentMessage(claim, paymentCycle, SCHEDULED_PAYMENT);

        verifyNonTopUpPaymentMessageSent(claim, paymentCycle, testStart, SCHEDULED_PAYMENT);
    }

    @Test
    void shouldReportScheduledPaymentWithBackdatedVouchers() {
        PaymentCycle paymentCycle = aPaymentCycleWithBackdatedVouchersOnly();
        LocalDateTime testStart = LocalDateTime.now();

        reportPaymentMessageSender.sendReportPaymentMessage(paymentCycle.getClaim(), paymentCycle, SCHEDULED_PAYMENT);

        verifyNonTopUpPaymentMessageSent(paymentCycle.getClaim(), paymentCycle, testStart, SCHEDULED_PAYMENT);
    }

    private void verifyNonTopUpPaymentMessageSent(Claim claim,
                                                  PaymentCycle paymentCycle,
                                                  LocalDateTime testStart,
                                                  PaymentAction paymentAction) {
        ArgumentCaptor<ReportPaymentMessagePayload> argumentCaptor = ArgumentCaptor.forClass(ReportPaymentMessagePayload.class);
        verify(messageQueueClient).sendMessage(argumentCaptor.capture(), eq(REPORT_PAYMENT));
        ReportPaymentMessagePayload payload = argumentCaptor.getValue();
        assertThat(payload.getTimestamp()).isAfterOrEqualTo(testStart);
        assertThat(payload.getClaimId()).isEqualTo(claim.getId());
        assertThat(payload.getPaymentCycleId()).isEqualTo(paymentCycle.getId());
        assertThat(payload.getPaymentAction()).isEqualTo(paymentAction);
        assertThat(payload.getIdentityAndEligibilityResponse()).isEqualTo(paymentCycle.getIdentityAndEligibilityResponse());
        PaymentCycleVoucherEntitlement voucherEntitlement = paymentCycle.getVoucherEntitlement();
        int voucherValue = voucherEntitlement.getSingleVoucherValueInPence();
        assertThat(payload.getPaymentForChildrenUnderOne()).isEqualTo(voucherEntitlement.getVouchersForChildrenUnderOne() * voucherValue);
        assertThat(payload.getPaymentForChildrenBetweenOneAndFour()).isEqualTo(voucherEntitlement.getVouchersForChildrenBetweenOneAndFour() * voucherValue);
        assertThat(payload.getPaymentForPregnancy()).isEqualTo(voucherEntitlement.getVouchersForPregnancy() * voucherValue);
        assertThat(payload.getPaymentForBackdatedVouchers()).isEqualTo(voucherEntitlement.getBackdatedVouchersValueInPence());
    }
}
