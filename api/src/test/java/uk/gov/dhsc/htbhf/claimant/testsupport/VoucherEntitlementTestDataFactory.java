package uk.gov.dhsc.htbhf.claimant.testsupport;

import uk.gov.dhsc.htbhf.claimant.entitlement.VoucherEntitlement;

import static uk.gov.dhsc.htbhf.claimant.testsupport.TestConstants.VOUCHERS_FOR_CHILDREN_BETWEEN_ONE_AND_FOUR;
import static uk.gov.dhsc.htbhf.claimant.testsupport.TestConstants.VOUCHERS_FOR_CHILDREN_UNDER_ONE;
import static uk.gov.dhsc.htbhf.claimant.testsupport.TestConstants.VOUCHERS_FOR_PREGNANCY;
import static uk.gov.dhsc.htbhf.claimant.testsupport.TestConstants.VOUCHER_VALUE_IN_PENCE;

public class VoucherEntitlementTestDataFactory {

    public static VoucherEntitlement aValidVoucherEntitlement() {
        return VoucherEntitlement.builder()
                .voucherValueInPence(VOUCHER_VALUE_IN_PENCE)
                .vouchersForChildrenUnderOne(VOUCHERS_FOR_CHILDREN_UNDER_ONE)
                .vouchersForChildrenBetweenOneAndFour(VOUCHERS_FOR_CHILDREN_BETWEEN_ONE_AND_FOUR)
                .vouchersForPregnancy(VOUCHERS_FOR_PREGNANCY)
                .build();
    }
}