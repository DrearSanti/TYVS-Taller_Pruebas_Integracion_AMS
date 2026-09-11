package edu.unisabana.tyvs.registry.infrastructure.persistence;

import edu.unisabana.tyvs.registry.application.usecase.Registry;
import edu.unisabana.tyvs.registry.domain.model.Gender;
import edu.unisabana.tyvs.registry.domain.model.Person;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contraparte H2 de RegistryRepositoryPostgresIT.shouldResolveQuotedLowercaseIdentifier().
 *
 * Ejecuta la MISMA consulta, sobre el MISMO esquema (RegistryRepository.initSchema),
 * pero contra H2. Juntas, las dos pruebas demuestran la divergencia con codigo
 * en ambos motores, en vez de afirmarla en un comentario:
 *
 *   - PostgreSQL pliega los identificadores sin comillas a minusculas -> "name" existe.
 *   - H2 los pliega a MAYUSCULAS -> solo existe "NAME", y "name" no se encuentra.
 *
 * No necesita Docker, por eso corre siempre. Se llama *IT porque abre una base
 * de datos real y pertenece al ciclo de mvn verify.
 */
@DisplayName("Divergencia de dialecto: la misma consulta contra H2")
class RegistryRepositoryH2DialectIT {

    /** Base propia para no compartir datos con otras clases que usan H2 en la misma JVM. */
    private static final String JDBC = "jdbc:h2:mem:regdb_dialect;DB_CLOSE_DELAY=-1";

    private RegistryRepository repo;
    private Registry registry;

    @BeforeEach
    void setUp() throws Exception {
        repo = new RegistryRepository(JDBC);
        repo.initSchema();
        repo.deleteAll();
        registry = new Registry(repo);
    }

    @Test
    @DisplayName("H2 NO resuelve un identificador entrecomillado en minusculas")
    void shouldFailOnQuotedLowercaseIdentifierInH2() throws Exception {
        // Arrange: el mismo dato que usa la prueba de PostgreSQL
        registry.registerVoter(new Person("Ana", 400, 30, Gender.FEMALE, true));

        try (Connection con = DriverManager.getConnection(JDBC, "", "");
             Statement st = con.createStatement()) {

            // Act + Assert: la consulta que en PostgreSQL funciona, en H2 falla
            SQLException error = assertThrows(SQLException.class,
                    () -> st.executeQuery("SELECT \"name\" FROM registry WHERE id = 400"));

            // Evidencia para el Wiki
            System.out.println("H2 rechazo la consulta: " + error.getMessage());
        }
    }

    @Test
    @DisplayName("En H2 la columna quedo guardada en MAYUSCULAS")
    void shouldFoldUnquotedIdentifiersToUppercaseInH2() throws Exception {
        // Arrange
        registry.registerVoter(new Person("Ana", 400, 30, Gender.FEMALE, true));

        // Act: el mismo identificador, pero en la caja que H2 realmente uso
        try (Connection con = DriverManager.getConnection(JDBC, "", "");
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT \"NAME\" FROM registry WHERE id = 400")) {

            // Assert
            assertTrue(rs.next());
            assertEquals("Ana", rs.getString(1));
        }
    }
}