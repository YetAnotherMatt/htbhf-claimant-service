package uk.gov.dhsc.htbhf.claimant.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.dhsc.htbhf.claimant.entity.Claim;
import uk.gov.dhsc.htbhf.claimant.repository.ClaimantRepository;

import static org.mockito.Mockito.verify;
import static uk.gov.dhsc.htbhf.claimant.testsupport.ClaimantTestDataFactory.aValidClaimant;

@ExtendWith(MockitoExtension.class)
public class ClaimServiceTest {

    @InjectMocks
    private ClaimService claimService;

    @Mock
    private ClaimantRepository claimantRepository;

    @Test
    public void shouldSaveClaimant() {
        //given
        Claim claim = Claim.builder()
                .claimant(aValidClaimant())
                .build();

        //when
        claimService.createClaim(claim);

        //then
        verify(claimantRepository).save(claim.getClaimant());
    }
}