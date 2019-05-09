package uk.gov.dhsc.htbhf.claimant.entitlement;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

/**
 * Calculates the entitlement for a claimant over a given payment cycle. Entitlement is calculated for a number of
 * calculation periods and the sum of those entitlements is returned. E.g. a cycle period of 28 days with 4 calculation
 * periods would result in entitlement being calculated four times, each one week apart.
 */
@Component
public class CycleEntitlementCalculator {

    private final Integer entitlementCalculationDuration;
    private final Integer numberOfCalculationPeriods;
    private final Integer weeksBeforeDueDate;
    private final Integer weeksAfterDueDate;
    private final EntitlementCalculator entitlementCalculator;
    private final BackDatedCycleEntitlementCalculator backDatedCycleEntitlementCalculator;

    /**
     * Constructor for {@link CycleEntitlementCalculator}.
     * @param paymentCycleConfig configuration for the payment cycle
     * @param entitlementCalculator calculates entitlement for a single calculation period
     * @param backDatedCycleEntitlementCalculator calculates back dated vouchers for previous cycles
     */
    public CycleEntitlementCalculator(PaymentCycleConfig paymentCycleConfig,
                                      EntitlementCalculator entitlementCalculator,
                                      BackDatedCycleEntitlementCalculator backDatedCycleEntitlementCalculator) {
        this.weeksBeforeDueDate = paymentCycleConfig.getWeeksBeforeDueDate();
        this.weeksAfterDueDate = paymentCycleConfig.getWeeksAfterDueDate();
        this.entitlementCalculationDuration = paymentCycleConfig.getEntitlementCalculationDurationInDays();
        this.numberOfCalculationPeriods = paymentCycleConfig.getNumberOfCalculationPeriods();
        this.entitlementCalculator = entitlementCalculator;
        this.backDatedCycleEntitlementCalculator = backDatedCycleEntitlementCalculator;
    }

    /**
     * Calculates the total voucher entitlement for a payment cycle when there is no previous voucher entitlement.
     *
     * @param expectedDueDate       expected due date
     * @param dateOfBirthOfChildren the date of birth of the claimant's children
     * @return the payment cycle voucher entitlement calculated for the claimant
     */
    public PaymentCycleVoucherEntitlement calculateEntitlement(Optional<LocalDate> expectedDueDate, List<LocalDate> dateOfBirthOfChildren) {
        List<VoucherEntitlement> voucherEntitlements = calculateCycleEntitlements(expectedDueDate, dateOfBirthOfChildren);
        return new PaymentCycleVoucherEntitlement(voucherEntitlements);
    }

    /**
     * Calculates the total voucher entitlement for a payment cycle given the previous entitlement.
     *
     * @param expectedDueDate            expected due date
     * @param dateOfBirthOfChildren      the date of birth of the claimant's children
     * @param previousVoucherEntitlement voucher entitlement from last payment cycle
     * @return the payment cycle voucher entitlement calculated for the claimant
     */
    public PaymentCycleVoucherEntitlement calculateEntitlement(Optional<LocalDate> expectedDueDate,
                                                               List<LocalDate> dateOfBirthOfChildren,
                                                               PaymentCycleVoucherEntitlement previousVoucherEntitlement) {
        List<LocalDate> newChildren = newChildrenMatchedToExpectedDeliveryDate(expectedDueDate, dateOfBirthOfChildren, previousVoucherEntitlement);
        if (newChildren.isEmpty()) {
            return calculateEntitlement(expectedDueDate, dateOfBirthOfChildren);
        }

        // ignore expected due date as we've determined that the pregnancy has happened
        List<VoucherEntitlement> voucherEntitlements = calculateCycleEntitlements(Optional.empty(), dateOfBirthOfChildren);
        Integer backdateVouchers = backDatedCycleEntitlementCalculator.calculateBackDatedVouchers(expectedDueDate, newChildren);
        return new PaymentCycleVoucherEntitlement(voucherEntitlements, backdateVouchers);
    }

    // move out of private method
    private List<VoucherEntitlement> calculateCycleEntitlements(Optional<LocalDate> expectedDueDate, List<LocalDate> dateOfBirthOfChildren) {
        List<LocalDate> entitlementDates = getCurrentCycleEntitlementDates();
        return entitlementDates.stream()
                .map(date -> entitlementCalculator.calculateVoucherEntitlement(expectedDueDate, dateOfBirthOfChildren, date))
                .collect(Collectors.toList());
    }

    // A child is considered a result of expected due date if their date of birth falls within a range of that date
    private List<LocalDate> newChildrenMatchedToExpectedDeliveryDate(Optional<LocalDate> expectedDueDate,
                                                                     List<LocalDate> dateOfBirthOfChildren,
                                                                     PaymentCycleVoucherEntitlement previousVoucherEntitlement) {
        if (noPregnancyVouchers(previousVoucherEntitlement) || notPregnant(expectedDueDate)) {
            return emptyList();
        }

        return dateOfBirthOfChildren.stream()
                .filter(date -> isWithinPregnancyMatchPeriod(expectedDueDate.get(), date))
                .collect(Collectors.toList());
    }

    private boolean notPregnant(Optional<LocalDate> expectedDueDate) {
        return expectedDueDate.isEmpty();
    }

    private boolean noPregnancyVouchers(PaymentCycleVoucherEntitlement vouchersForPregnancy) {
        return vouchersForPregnancy.getVouchersForPregnancy() == 0;
    }

    private boolean isWithinPregnancyMatchPeriod(LocalDate expectedDueDate, LocalDate dateOfBirth) {
        LocalDate startDate = expectedDueDate.minusWeeks(weeksBeforeDueDate);
        LocalDate endDate = expectedDueDate.plusWeeks(weeksAfterDueDate);

        return !dateOfBirth.isBefore(startDate) && !dateOfBirth.isAfter(endDate);
    }

    private List<LocalDate> getCurrentCycleEntitlementDates() {
        LocalDate today = LocalDate.now();
        List<LocalDate> entitlementDates = new ArrayList<>(numberOfCalculationPeriods);
        for (int i = 0; i < numberOfCalculationPeriods; i++) {
            entitlementDates.add(today.plusDays(i * entitlementCalculationDuration));
        }
        return entitlementDates;
    }
}