package edu.unisabana.tyvs.registry.application.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

import edu.unisabana.tyvs.registry.application.port.out.RegistryRepositoryPort;
import edu.unisabana.tyvs.registry.domain.model.Gender;
import edu.unisabana.tyvs.registry.domain.model.Person;
import edu.unisabana.tyvs.registry.domain.model.RegisterResult;
import edu.unisabana.tyvs.registry.infrastructure.persistence.RegistryRepository;

/**
 * Pruebas de integracion para el caso de uso {@link Registry}, aplicando el formato AAA:
 * <ul>
 *   <li><b>Arrange</b>: preparacion de datos y objetos a probar.</li>
 *   <li><b>Act</b>: ejecucion del metodo bajo prueba.</li>
 *   <li><b>Assert</b>: verificacion de los resultados esperados.</li>
 * </ul>
 *
 * <p>A diferencia de {@code RegistryWithMockTest}, aqui el colaborador es un
 * {@link RegistryRepository} real sobre H2. Eso permite afirmar no solo que el
 * caso de uso devuelve el resultado correcto, sino que los datos quedaron (o no
 * quedaron) realmente en la tabla.</p>
 */
public class RegistryIT {

    private RegistryRepositoryPort repo;
    private Registry registry;

    /**
     * Arrange comun a todos los tests:
     * <ul>
     *   <li>Instancia un repositorio H2 en memoria.</li>
     *   <li>Inicializa el esquema (tabla) y limpia datos previos.</li>
     *   <li>Construye el caso de uso inyectando el repositorio.</li>
     * </ul>
     */
    @Before
    public void setup() throws Exception {
        String jdbc = "jdbc:h2:mem:regdb;DB_CLOSE_DELAY=-1";
        repo = new RegistryRepository(jdbc);

        repo.initSchema();   // Arrange: crear tabla
        repo.deleteAll();    // Arrange: limpiar datos previos

        registry = new Registry(repo); // Arrange: inyectar dependencia
    }

    /**
     * Caso de prueba:
     * <p>Una persona valida debe ser registrada exitosamente.</p>
     */
    @Test
    public void shouldRegisterValidPerson() throws Exception {
        // Arrange
        Person p1 = new Person("Ana", 100, 30, Gender.FEMALE, true);

        // Act
        RegisterResult result = registry.registerVoter(p1);

        // Assert
        assertEquals(RegisterResult.VALID, result);
        assertTrue(repo.existsById(100));
    }

    /**
     * Caso de prueba:
     * <p>Al intentar registrar dos personas con el mismo ID:</p>
     * <ul>
     *   <li>La primera se guarda como valida.</li>
     *   <li>La segunda es rechazada como duplicada.</li>
     * </ul>
     */
    @Test
    public void shouldPersistValidVoterAndRejectDuplicates() throws Exception {
        // Arrange
        Person p1 = new Person("Ana", 100, 30, Gender.FEMALE, true);
        Person p2 = new Person("AnaDos", 100, 40, Gender.FEMALE, true);

        // Act (primer registro)
        RegisterResult result1 = registry.registerVoter(p1);

        // Assert primer registro
        assertEquals(RegisterResult.VALID, result1);
        assertTrue(repo.existsById(100));

        // Act (segundo registro con mismo ID)
        RegisterResult result2 = registry.registerVoter(p2);

        // Assert segundo registro
        assertEquals(RegisterResult.DUPLICATED, result2);
    }

    /**
     * Caso de prueba:
     * <p>Una persona menor de edad es rechazada y NO debe quedar persistida.</p>
     *
     * <p>Datos elegidos para aislar la regla: viva, id positivo y edad dentro del
     * rango biologico posible. Lo unico que falla es que no alcanza
     * {@code MIN_AGE}.</p>
     */
    @Test
    public void shouldReturnUnderageWhenPersonIsMinor() throws Exception {
        // Arrange
        Person menor = new Person("Sofia", 101, 17, Gender.FEMALE, true);

        // Act
        RegisterResult result = registry.registerVoter(menor);

        // Assert
        assertEquals(RegisterResult.UNDERAGE, result);
        assertFalse("Un menor de edad no debe quedar en la tabla", repo.existsById(101));
    }

    /**
     * Caso de prueba:
     * <p>Una edad fuera del rango biologico posible se rechaza como dato invalido,
     * no como menor de edad.</p>
     *
     * <p>Este caso protege el orden de las validaciones en {@link Registry}: si
     * {@code INVALID_AGE} se evaluara despues de {@code UNDERAGE}, una edad de -1
     * caeria en la rama equivocada.</p>
     */
    @Test
    public void shouldReturnInvalidAgeWhenAgeIsNegative() throws Exception {
        // Arrange
        Person edadImposible = new Person("Carlos", 102, -1, Gender.MALE, true);

        // Act
        RegisterResult result = registry.registerVoter(edadImposible);

        // Assert
        assertEquals(RegisterResult.INVALID_AGE, result);
        assertFalse(repo.existsById(102));
    }

    /**
     * Caso de prueba:
     * <p>Una persona fallecida es rechazada antes de cualquier validacion de edad.</p>
     *
     * <p>Se usa una edad y un id perfectamente validos a proposito: si tambien
     * fueran invalidos, la prueba pasaria sin demostrar cual regla produjo el
     * rechazo.</p>
     */
    @Test
    public void shouldReturnDeadWhenPersonIsNotAlive() throws Exception {
        // Arrange
        Person fallecido = new Person("Rosa", 103, 45, Gender.FEMALE, false);

        // Act
        RegisterResult result = registry.registerVoter(fallecido);

        // Assert
        assertEquals(RegisterResult.DEAD, result);
        assertFalse(repo.existsById(103));
    }

    /**
     * Caso de prueba (valor limite):
     * <p>Una persona con exactamente {@code MIN_AGE} anios si puede votar.</p>
     *
     * <p>Detecta un error clasico de frontera: escribir {@code <=} donde va
     * {@code <} rechazaria a todos los de 18 anios sin que ninguna otra prueba
     * de esta clase lo notara.</p>
     */
    @Test
    public void shouldRegisterPersonAtMinimumVotingAge() throws Exception {
        // Arrange
        Person justoMayor = new Person("Diego", 104, Registry.MIN_AGE, Gender.MALE, true);

        // Act
        RegisterResult result = registry.registerVoter(justoMayor);

        // Assert
        assertEquals(RegisterResult.VALID, result);
        assertTrue(repo.existsById(104));
    }
}