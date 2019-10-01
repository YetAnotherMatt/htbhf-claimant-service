package uk.gov.dhsc.htbhf.claimant.creator.entities.dwp.uc;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import uk.gov.dhsc.htbhf.claimant.creator.entities.dwp.Child;

import java.time.LocalDate;
import javax.persistence.*;

@Entity
@Data
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "dwp_uc_child")
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
public class UCChild extends Child {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dwp_uc_household_id", nullable = false)
    private UCHousehold household;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

}
