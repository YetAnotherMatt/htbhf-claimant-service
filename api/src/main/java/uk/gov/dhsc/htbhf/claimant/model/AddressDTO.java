package uk.gov.dhsc.htbhf.claimant.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import static uk.gov.dhsc.htbhf.claimant.regex.PostcodeRegex.UK_POST_CODE_REGEX;

@Data
@Builder
@AllArgsConstructor(onConstructor_ = {@JsonCreator})
@ApiModel(description = "Multi purpose address object")
public class AddressDTO {

    @NotNull
    @Size(min = 1, max = 500)
    @JsonProperty("addressLine1")
    @ApiModelProperty(notes = "First line of the address", example = "Flat B")
    private String addressLine1;

    @Size(max = 500)
    @JsonProperty("addressLine2")
    @ApiModelProperty(notes = "Second line of the address", example = "221 Baker Street")
    private String addressLine2;

    @NotNull
    @Size(min = 1, max = 500)
    @JsonProperty("townOrCity")
    @ApiModelProperty(notes = "Town or city of the address", example = "London")
    private String townOrCity;

    @NotNull
    @Pattern(regexp = UK_POST_CODE_REGEX, message = "invalid postcode format")
    @JsonProperty("postcode")
    @ApiModelProperty(notes = "The postcode of the address.", example = "AA1 1AA")
    private String postcode;
}