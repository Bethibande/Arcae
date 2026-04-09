package com.bethibande.arcae.web.repositories;

import com.bethibande.arcae.jpa.repository.PackageManager;
import com.bethibande.arcae.repository.maven.MavenRepositoryConfig;
import com.bethibande.arcae.repository.mirror.StandardMirrorConfig;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;

import java.util.List;

@QuarkusTest
@TestHTTPEndpoint(MavenRepositoryEndpoint.class)
public class MavenRepositoryTests extends AbstractRepositoryTests {

    @BeforeAll
    public static void setup() {
        createRepository(
                "maven",
                PackageManager.MAVEN,
                new MavenRepositoryConfig(
                        false,
                        getS3Config(),
                        new StandardMirrorConfig(
                                List.of(),
                                false,
                                false,
                                false
                        )
                ),
                List.of()
        );
    }

}
