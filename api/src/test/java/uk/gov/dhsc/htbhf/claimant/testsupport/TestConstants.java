package uk.gov.dhsc.htbhf.claimant.testsupport;

import java.time.LocalDate;

public class TestConstants {

    public static final String VALID_NINO = "EB123456C";
    public static final String VALID_FIRST_NAME = "James";
    public static final String VALID_LAST_NAME = "Smith";
    public static final LocalDate VALID_DOB = LocalDate.parse("1985-12-31");
    public static final String VALID_ADDRESS_LINE_1 = "Flat b";
    public static final String VALID_ADDRESS_LINE_2 = "123 Fake street";
    public static final String VALID_TOWN_OR_CITY = "Springfield";
    public static final String VALID_POSTCODE = "AA1 1AA";
    public static final String VALID_PHONE_NUMBER = "+447123456789";
    public static final String VALID_EMAIL_ADDRESS = "test@email.com";

    public static final String DWP_HOUSEHOLD_IDENTIFIER = "dwpHousehold1";
    public static final String HMRC_HOUSEHOLD_IDENTIFIER = "hmrcHousehold1";

    public static final int NUMBER_OF_CHILDREN_UNDER_ONE = 1;
    public static final int NUMBER_OF_CHILDREN_UNDER_FOUR = 2;

    public static final int VOUCHER_VALUE_IN_PENCE = 310;
    public static final int VOUCHERS_FOR_CHILDREN_UNDER_ONE = 2;
    public static final int VOUCHERS_FOR_CHILDREN_BETWEEN_ONE_AND_FOUR = 1;
    public static final int VOUCHERS_FOR_PREGNANCY = 1;
    public static final int TOTAL_VOUCHER_ENTITLEMENT = 4;
    public static final int TOTAL_VOUCHER_VALUE_IN_PENCE = 1240;

    public static final LocalDate JAMES_DATE_OF_BIRTH = VALID_DOB;
    public static final String JAMES_FIRST_NAME = "James";
    public static final String JAMES_LAST_NAME = "Smith";

    public static final LocalDate MAGGIE_DOB = LocalDate.now().minusMonths(6);
    public static final LocalDate LISA_DOB = LocalDate.now().minusMonths(24);

    public static final String CARD_ACCOUNT_ID = "123456789";

    public static final int AVAILABLE_BALANCE_IN_PENCE = 100;
    public static final int LEDGER_BALANCE_IN_PENCE = 100;
}
