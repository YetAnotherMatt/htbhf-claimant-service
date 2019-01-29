package uk.gov.dhsc.htbhf.claimant.converter;

import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import uk.gov.dhsc.htbhf.claimant.entity.Address;
import uk.gov.dhsc.htbhf.claimant.model.AddressDTO;

/**
 * Converts a {@link AddressDTO} into a {@link Address}.
 */
@Component
public class AddressDTOToAddressConverter {

    public Address convert(AddressDTO source) {
        Assert.notNull(source, "source AddressDTO must not be null");
        return Address.builder()
                .addressLine1(source.getAddressLine1())
                .addressLine2(source.getAddressLine2())
                .townOrCity(source.getTownOrCity())
                .postcode(source.getPostcode())
                .build();

    }
}
