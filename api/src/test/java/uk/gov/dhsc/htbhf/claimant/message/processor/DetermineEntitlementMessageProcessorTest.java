package uk.gov.dhsc.htbhf.claimant.message.processor;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.dhsc.htbhf.claimant.entitlement.PaymentCycleVoucherEntitlement;
import uk.gov.dhsc.htbhf.claimant.entitlement.VoucherEntitlement;
import uk.gov.dhsc.htbhf.claimant.entity.Claim;
import uk.gov.dhsc.htbhf.claimant.entity.Message;
import uk.gov.dhsc.htbhf.claimant.entity.PaymentCycle;
import uk.gov.dhsc.htbhf.claimant.message.MessageQueueDAO;
import uk.gov.dhsc.htbhf.claimant.message.MessageStatus;
import uk.gov.dhsc.htbhf.claimant.message.context.DetermineEntitlementMessageContext;
import uk.gov.dhsc.htbhf.claimant.message.context.MessageContextLoader;
import uk.gov.dhsc.htbhf.claimant.message.payload.MakePaymentMessagePayload;
import uk.gov.dhsc.htbhf.claimant.model.eligibility.EligibilityAndEntitlementDecision;
import uk.gov.dhsc.htbhf.claimant.service.EligibilityAndEntitlementService;
import uk.gov.dhsc.htbhf.claimant.service.payments.PaymentCycleService;
import uk.gov.dhsc.htbhf.eligibility.model.EligibilityStatus;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static uk.gov.dhsc.htbhf.claimant.message.MessageStatus.COMPLETED;
import static uk.gov.dhsc.htbhf.claimant.message.MessageType.DETERMINE_ENTITLEMENT;
import static uk.gov.dhsc.htbhf.claimant.message.MessageType.MAKE_PAYMENT;
import static uk.gov.dhsc.htbhf.claimant.testsupport.ClaimTestDataFactory.aClaimWithExpectedDeliveryDate;
import static uk.gov.dhsc.htbhf.claimant.testsupport.EligibilityAndEntitlementTestDataFactory.aDecisionWithStatus;
import static uk.gov.dhsc.htbhf.claimant.testsupport.MessageContextTestDataFactory.aDetermineEntitlementMessageContext;
import static uk.gov.dhsc.htbhf.claimant.testsupport.MessagePayloadTestDataFactory.aMakePaymentPayload;
import static uk.gov.dhsc.htbhf.claimant.testsupport.MessageTestDataFactory.aValidMessageWithType;
import static uk.gov.dhsc.htbhf.claimant.testsupport.PaymentCycleTestDataFactory.aPaymentCycleWithCycleStartDateAndClaim;
import static uk.gov.dhsc.htbhf.claimant.testsupport.PaymentCycleTestDataFactory.aPaymentCycleWithCycleStartDateEntitlementAndClaim;
import static uk.gov.dhsc.htbhf.claimant.testsupport.PaymentCycleVoucherEntitlementTestDataFactory.aPaymentCycleVoucherEntitlementWithEntitlement;
import static uk.gov.dhsc.htbhf.claimant.testsupport.VoucherEntitlementTestDataFactory.aVoucherEntitlementWithEntitlementDate;
import static uk.gov.dhsc.htbhf.eligibility.model.EligibilityStatus.ELIGIBLE;

@ExtendWith(MockitoExtension.class)
class DetermineEntitlementMessageProcessorTest {

    @Mock
    private EligibilityAndEntitlementService eligibilityAndEntitlementService;
    @Mock
    private MessageContextLoader messageContextLoader;
    @Mock
    private PaymentCycleService paymentCycleService;
    @Mock
    private MessageQueueDAO messageQueueDAO;

    @InjectMocks
    private DetermineEntitlementMessageProcessor processor;

    @ParameterizedTest
    @EnumSource(EligibilityStatus.class)
    void shouldSuccessfullyProcessMessageAndTriggerPaymentWhenClaimantIsEligible(EligibilityStatus eligibilityStatus) {
        //Given
        LocalDate expectedDeliveryDate = LocalDate.now();
        DetermineEntitlementMessageContext context = buildMessageContextWithExpectedDeliveryDate(expectedDeliveryDate);
        given(messageContextLoader.loadDetermineEntitlementContext(any())).willReturn(context);

        //Eligibility
        EligibilityAndEntitlementDecision decision = aDecisionWithStatus(eligibilityStatus);
        given(eligibilityAndEntitlementService.evaluateExistingClaimant(any(), any(), any())).willReturn(decision);

        // expected delivery date
        given(paymentCycleService.getExpectedDeliveryDateIfRelevant(any(), any())).willReturn(expectedDeliveryDate);

        //Current payment cycle voucher entitlement mocking
        Message message = aValidMessageWithType(DETERMINE_ENTITLEMENT);

        //When
        MessageStatus messageStatus = processor.processMessage(message);

        //Then
        assertThat(messageStatus).isEqualTo(COMPLETED);
        verify(messageContextLoader).loadDetermineEntitlementContext(message);
        verify(eligibilityAndEntitlementService).evaluateExistingClaimant(context.getClaim().getClaimant(),
                context.getCurrentPaymentCycle().getCycleStartDate(),
                context.getPreviousPaymentCycle());

        verifyPaymentCycleSavedWithDecision(context.getCurrentPaymentCycle(), decision, context.getClaim(), expectedDeliveryDate);
        if (eligibilityStatus == ELIGIBLE) {
            MakePaymentMessagePayload expectedPaymentMessagePayload = aMakePaymentPayload(context.getClaim().getId(), context.getCurrentPaymentCycle().getId());
            verify(messageQueueDAO).sendMessage(expectedPaymentMessagePayload, MAKE_PAYMENT);
        } else {
            verifyZeroInteractions(messageQueueDAO);
        }
    }

    private void verifyPaymentCycleSavedWithDecision(PaymentCycle paymentCycle,
                                                     EligibilityAndEntitlementDecision decision,
                                                     Claim claim,
                                                     LocalDate expectedDeliveryDate) {
        ArgumentCaptor<PaymentCycle> argumentCaptor = ArgumentCaptor.forClass(PaymentCycle.class);
        verify(paymentCycleService).getExpectedDeliveryDateIfRelevant(claim, paymentCycle.getVoucherEntitlement());
        verify(paymentCycleService).savePaymentCycle(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues()).hasSize(1);
        PaymentCycle savedPaymentCycle = argumentCaptor.getValue();
        assertThat(savedPaymentCycle.getId()).isEqualTo(paymentCycle.getId());
        assertThat(savedPaymentCycle.getEligibilityStatus()).isEqualTo(decision.getEligibilityStatus());
        assertThat(savedPaymentCycle.getChildrenDob()).isEqualTo(decision.getDateOfBirthOfChildren());
        assertThat(savedPaymentCycle.getTotalEntitlementAmountInPence()).isEqualTo(decision.getVoucherEntitlement().getTotalVoucherValueInPence());
        assertThat(savedPaymentCycle.getTotalVouchers()).isEqualTo(decision.getVoucherEntitlement().getTotalVoucherEntitlement());
        assertThat(savedPaymentCycle.getExpectedDeliveryDate()).isEqualTo(expectedDeliveryDate);
    }

    private DetermineEntitlementMessageContext buildMessageContextWithExpectedDeliveryDate(LocalDate expectedDeliveryDate) {
        //Claim
        Claim claim = aClaimWithExpectedDeliveryDate(expectedDeliveryDate);

        //Current payment cycle
        LocalDate cycleStartDate = LocalDate.now();
        PaymentCycle currentPaymentCycle = aPaymentCycleWithCycleStartDateAndClaim(cycleStartDate, claim);

        //Previous payment cycle
        LocalDate previousCycleStartDate = LocalDate.now().minusWeeks(4);
        VoucherEntitlement previousVoucherEntitlement = aVoucherEntitlementWithEntitlementDate(previousCycleStartDate);
        PaymentCycleVoucherEntitlement previousPaymentCycleVoucherEntitlement = aPaymentCycleVoucherEntitlementWithEntitlement(previousVoucherEntitlement);
        PaymentCycle previousPaymentCycle = aPaymentCycleWithCycleStartDateEntitlementAndClaim(previousCycleStartDate,
                previousPaymentCycleVoucherEntitlement,
                claim);

        return aDetermineEntitlementMessageContext(
                currentPaymentCycle,
                previousPaymentCycle,
                claim);
    }

}