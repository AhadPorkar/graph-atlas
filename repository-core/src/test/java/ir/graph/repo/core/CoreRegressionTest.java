package ir.graph.repo.core;

import org.junit.jupiter.api.Test;

class CoreRegressionTest {
    @Test void rawUriPolicy() { RequestPathPolicyTestMain.main(new String[0]); }
    @Test void releaseGovernanceAndEvidence() throws Exception { ReleaseContractTestMain.main(new String[0]); }
    @Test void localeNegotiation() { LocalizationTestMain.main(new String[0]); }
    @Test void storageAndUtilities() throws Exception { SelfTest.main(new String[0]); }
    @Test void securityAndPackageContracts() throws Exception { CoreContractTestMain.main(new String[0]); }
}
