package io.floci.aws.services.iam;

import io.floci.aws.core.common.RegionResolver;
import io.floci.aws.core.storage.InMemoryStorage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code floci.services.iam.outbound-federation-issuer-id} fixes the UUID the default account's
 * issuer is minted with, so a relying party can be given the issuer and its JWKS URL before Floci
 * starts. Unset, the issuer is random, as AWS mints it.
 */
class OutboundFederationIssuerIdTest {

    private static final String DEFAULT_ACCOUNT = "000000000000";
    private static final String OTHER_ACCOUNT = "111111111111";
    private static final String ISSUER_ID = "0b7c2f4e-8d1a-4c3b-9e5f-6a7b8c9d0e1f";
    private static final String RANDOM_ISSUER =
            "https://[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.tokens\\.sts\\.global\\.api\\.aws";

    private static IamService newService(RegionResolver regionResolver) {
        return new IamService(
                new InMemoryStorage<>(), new InMemoryStorage<>(), new InMemoryStorage<>(),
                new InMemoryStorage<>(), new InMemoryStorage<>(), new InMemoryStorage<>(),
                new InMemoryStorage<>(), new InMemoryStorage<>(), new InMemoryStorage<>(),
                new InMemoryStorage<>(), new InMemoryStorage<>(), new InMemoryStorage<>(),
                new InMemoryStorage<>(), new InMemoryStorage<>(), new InMemoryStorage<>(),
                new InMemoryStorage<>(), new InMemoryStorage<>(), new InMemoryStorage<>(),
                new InMemoryStorage<>(), new InMemoryStorage<>(), new InMemoryStorage<>(),
                new InMemoryStorage<>(), regionResolver, false, null);
    }

    private static IamService inDefaultAccount() {
        return newService(new RegionResolver("us-east-1", DEFAULT_ACCOUNT));
    }

    @Test
    void theDefaultAccountsIssuerIsMintedWithTheConfiguredId() {
        IamService iam = inDefaultAccount();
        iam.useOutboundFederationIssuerId(ISSUER_ID);

        assertEquals("https://" + ISSUER_ID + ".tokens.sts.global.api.aws",
                iam.enableOutboundWebIdentityFederation());
    }

    @Test
    void withoutOneTheIssuerIsRandom() {
        String first = inDefaultAccount().enableOutboundWebIdentityFederation();
        String second = inDefaultAccount().enableOutboundWebIdentityFederation();

        assertTrue(first.matches(RANDOM_ISSUER), first);
        assertNotEquals(first, second);
    }

    /** Every account has its own issuer, so only the default account's is fixed. */
    @Test
    void anotherAccountsIssuerStaysRandom() {
        IamService iam = newService(new RegionResolver("us-east-1", DEFAULT_ACCOUNT) {
            @Override
            public String getAccountId() {
                return OTHER_ACCOUNT;
            }
        });
        iam.useOutboundFederationIssuerId(ISSUER_ID);

        String issuer = iam.enableOutboundWebIdentityFederation();

        assertTrue(issuer.matches(RANDOM_ISSUER), issuer);
        assertNotEquals("https://" + ISSUER_ID + ".tokens.sts.global.api.aws", issuer);
    }

    /** An issuer AWS would never mint is refused at startup rather than handed to a relying party. */
    @Test
    void anIdThatIsNotALowercaseUuidIsRefused() {
        IamService iam = inDefaultAccount();

        for (String id : new String[] {"not-a-uuid", ISSUER_ID.toUpperCase(), "", ISSUER_ID + "0"}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> iam.useOutboundFederationIssuerId(id));
            assertTrue(error.getMessage().contains("outbound-federation-issuer-id"), error.getMessage());
        }
    }
}
