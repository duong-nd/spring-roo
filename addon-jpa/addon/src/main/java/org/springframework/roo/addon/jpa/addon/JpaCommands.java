package org.springframework.roo.addon.jpa.addon;

import static org.springframework.roo.model.GoogleJavaType.GAE_DATASTORE_KEY;
import static org.springframework.roo.model.JavaType.LONG_OBJECT;
import static org.springframework.roo.model.RooJavaType.ROO_EQUALS;
import static org.springframework.roo.model.RooJavaType.ROO_JAVA_BEAN;
import static org.springframework.roo.model.RooJavaType.ROO_JPA_ENTITY;
import static org.springframework.roo.model.RooJavaType.ROO_SERIALIZABLE;
import static org.springframework.roo.model.RooJavaType.ROO_TO_STRING;
import static org.springframework.roo.shell.OptionContexts.INTERFACE;
import static org.springframework.roo.shell.OptionContexts.SUPERCLASS;
import static org.springframework.roo.shell.OptionContexts.UPDATE_PROJECT;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.lang3.Validate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;
import org.springframework.roo.addon.jpa.annotations.entity.RooJpaEntity;
import org.springframework.roo.addon.propfiles.PropFileOperations;
import org.springframework.roo.addon.test.addon.IntegrationTestOperations;
import org.springframework.roo.classpath.TypeLocationService;
import org.springframework.roo.classpath.details.BeanInfoUtils;
import org.springframework.roo.classpath.details.ClassOrInterfaceTypeDetails;
import org.springframework.roo.classpath.details.annotations.AnnotationMetadataBuilder;
import org.springframework.roo.classpath.operations.InheritanceType;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.model.ReservedWords;
import org.springframework.roo.project.ProjectOperations;
import org.springframework.roo.project.settings.ProjectSettingsService;
import org.springframework.roo.shell.CliAvailabilityIndicator;
import org.springframework.roo.shell.CliCommand;
import org.springframework.roo.shell.CliOption;
import org.springframework.roo.shell.CliOptionMandatoryIndicator;
import org.springframework.roo.shell.CliOptionVisibilityIndicator;
import org.springframework.roo.shell.CommandMarker;
import org.springframework.roo.shell.ShellContext;
import org.springframework.roo.shell.converters.StaticFieldConverter;
import org.springframework.roo.support.logging.HandlerUtils;

/**
 * Commands for the JPA add-on to be used by the ROO shell.
 * 
 * @author Stefan Schmidt
 * @author Ben Alex
 * @author Alan Stewart
 * @author Juan Carlos García
 * @since 1.0
 */
@Component
@Service
public class JpaCommands implements CommandMarker {

    private static Logger LOGGER = HandlerUtils.getLogger(JpaCommands.class);

    // Project Settings 
    private static final String SPRING_ROO_JPA_REQUIRE_TABLE_NAME = "spring.roo.jpa.require.table-name";
    private static final String SPRING_ROO_JPA_REQUIRE_COLUMN_NAME = "spring.roo.jpa.require.column-name";
    private static final String SPRING_ROO_JPA_REQUIRE_SEQUENCE_NAME = "spring.roo.jpa.require.sequence-name";
    
    // Annotations
    private static final AnnotationMetadataBuilder ROO_EQUALS_BUILDER = new AnnotationMetadataBuilder(
            ROO_EQUALS);
    private static final AnnotationMetadataBuilder ROO_JAVA_BEAN_BUILDER = new AnnotationMetadataBuilder(
            ROO_JAVA_BEAN);
    private static final AnnotationMetadataBuilder ROO_SERIALIZABLE_BUILDER = new AnnotationMetadataBuilder(
            ROO_SERIALIZABLE);
    private static final AnnotationMetadataBuilder ROO_TO_STRING_BUILDER = new AnnotationMetadataBuilder(
            ROO_TO_STRING);

    @Reference private IntegrationTestOperations integrationTestOperations;
    @Reference private JpaOperations jpaOperations;
    @Reference private ProjectOperations projectOperations;
    @Reference private PropFileOperations propFileOperations;
    @Reference private StaticFieldConverter staticFieldConverter;
    @Reference private TypeLocationService typeLocationService;
    @Reference private ProjectSettingsService projectSettings;
    
    protected void activate(final ComponentContext context) {
        staticFieldConverter.add(JdbcDatabase.class);
        staticFieldConverter.add(OrmProvider.class);
    }

    protected void deactivate(final ComponentContext context) {
        staticFieldConverter.remove(JdbcDatabase.class);
        staticFieldConverter.remove(OrmProvider.class);
    }
    
    @CliAvailabilityIndicator({ "jpa setup" })
    public boolean isJpaSetupAvailable() {
        return jpaOperations.isJpaInstallationPossible();
    }

    @CliAvailabilityIndicator({ "entity jpa", "embeddable" })
    public boolean isClassGenerationAvailable() {
        return jpaOperations.hasSpringDataDependency();
    }

    @CliCommand(value = "embeddable", help = "Creates a new Java class source file with the JPA @Embeddable annotation in SRC_MAIN_JAVA")
    public void createEmbeddableClass(
            @CliOption(key = "class", optionContext = UPDATE_PROJECT, mandatory = true, help = "The name of the class to create") final JavaType name,
            @CliOption(key = "serializable", mandatory = false, unspecifiedDefaultValue = "false", specifiedDefaultValue = "true", help = "Whether the generated class should implement java.io.Serializable") final boolean serializable,
            @CliOption(key = "permitReservedWords", mandatory = false, unspecifiedDefaultValue = "false", specifiedDefaultValue = "true", help = "Indicates whether reserved words are ignored by Roo") final boolean permitReservedWords) {

        if (!permitReservedWords) {
            ReservedWords.verifyReservedWordsNotPresent(name);
        }

        jpaOperations.newEmbeddableClass(name, serializable);
    }

    @CliOptionVisibilityIndicator(command = "jpa setup", params = {
            "jndiDataSource"}, help = "jndiDataSource parameter is not available if any of databaseName, hostName, password or userName are selected or you are using an HYPERSONIC database.")
    public boolean isJndiVisible(ShellContext shellContext) {

        Map<String, String> params = shellContext.getParameters();
        
        // If mandatory parameter database is not defined, all parameters are not visible
        String database = params.get("database");
        if(database == null){
            return false;
        }
        
        // If uses some HYPERSONIC database, jndiDataSource should not be visible.
        if(database.startsWith("HYPERSONIC")){
            return false;
        }

        // If user define databaseName, hostName, password or username parameters, jndiDataSource
        // should not be visible.
        if (params.containsKey("databaseName") || params.containsKey("hostName") || params.containsKey("password") || params.containsKey("userName")) {
            return false;
        }

        return true;
    }
    
    @CliOptionVisibilityIndicator(command = "jpa setup", params = {
    "databaseName", "hostName", "password", "userName"}, help = "Connection parameters are not available if jndiDatasource is specified or you are using an HYPERSONIC database.")
    public boolean areConnectionParamsVisible(ShellContext shellContext) {

        Map<String, String> params = shellContext.getParameters();

        // If mandatory parameter database is not defined, all parameters are not visible
        String database= params.get("database");
        if(database == null){
            return false;
        }
        
        // If uses some HYPERSONIC database, jndiDataSource parameter should not be visible.
        if(database.startsWith("HYPERSONIC")){
            return false;
        }
        
        // If user define jndiDatasource parameter, connection parameters should not be visible
        if (params.containsKey("jndiDataSource")) {
            return false;
        }
        
        return true;
    }
    
    @CliCommand(value = "jpa setup", help = "Install or updates a JPA persistence provider in your project")
    public void installJpa(
            @CliOption(key = "provider", mandatory = true, help = "The persistence provider to support") final OrmProvider ormProvider,
            @CliOption(key = "database", mandatory = true, help = "The database to support") final JdbcDatabase jdbcDatabase,
            @CliOption(key = "jndiDataSource", mandatory = false, help = "The JNDI datasource to use") final String jndi,
            @CliOption(key = "hostName", mandatory = false, help = "The host name to use") final String hostName,
            @CliOption(key = "databaseName", mandatory = false, help = "The database name to use") final String databaseName,
            @CliOption(key = "userName", mandatory = false, help = "The username to use") final String userName,
            @CliOption(key = "password", mandatory = false, help = "The password to use") final String password,
            ShellContext shellContext) {

        if (jdbcDatabase == JdbcDatabase.FIREBIRD && !isJdk6OrHigher()) {
            LOGGER.warning("JDK must be 1.6 or higher to use Firebird");
            return;
        }

        jpaOperations.configureJpa(ormProvider, jdbcDatabase, jndi,
                hostName, databaseName, userName, password,
                projectOperations.getFocusedModuleName(), 
                shellContext.getProfile(), shellContext.isForce());
    }
    
    /**
     * ROO-3709: Indicator that checks if exists some project setting that makes
     * table parameter mandatory.
     * 
     * @param shellContext
     * @return true if exists property
     *         {@link #SPRING_ROO_JPA_REQUIRE_TABLE_NAME} on project settings
     *         and its value is "true". If not, return false.
     */
    @CliOptionMandatoryIndicator(params = { "table" }, command = "entity jpa")
    public boolean isTableMandatory(ShellContext shellContext) {

        // Check if property 'spring.roo.jpa.require.table-name' is defined on
        // project settings
        String requiredTableName = projectSettings
                .getProperty(SPRING_ROO_JPA_REQUIRE_TABLE_NAME);

        if (requiredTableName != null && requiredTableName.equals("true")) {
            return true;
        }

        return false;
    }
    
    /**
     * ROO-3709: Indicator that checks if exists some project setting that makes
     * identifierColumn parameter mandatory.
     * 
     * @param shellContext
     * @return true if exists property
     *         {@link #SPRING_ROO_JPA_REQUIRE_COLUMN_NAME} on project settings
     *         and its value is "true". If not, return false.
     */
    @CliOptionMandatoryIndicator(params = { "identifierColumn" }, command = "entity jpa")
    public boolean isColumnMandatory(ShellContext shellContext) {

        // Check if property 'spring.roo.jpa.require.column-name' is defined on
        // project settings
        String requiredColumnName = projectSettings
                .getProperty(SPRING_ROO_JPA_REQUIRE_COLUMN_NAME);

        if (requiredColumnName != null && requiredColumnName.equals("true")) {
            return true;
        }

        return false;
    }
    
    /**
     * ROO-3709: Indicator that checks if exists some project setting that makes
     * sequenceName parameter mandatory.
     * 
     * @param shellContext
     * @return true if exists property
     *         {@link #SPRING_ROO_JPA_REQUIRE_SEQUENCE_NAME} on project settings
     *         and its value is "true". If not, return false.
     */
    @CliOptionMandatoryIndicator(params = { "sequenceName" }, command = "entity jpa")
    public boolean isSequenceNameMandatory(ShellContext shellContext) {

        // Check if property 'spring.roo.jpa.require.sequence-name' is defined on
        // project settings
        String requiredSequenceName = projectSettings
                .getProperty(SPRING_ROO_JPA_REQUIRE_SEQUENCE_NAME);

        if (requiredSequenceName != null && requiredSequenceName.equals("true")) {
            return true;
        }

        return false;
    }

    @CliCommand(value = "entity jpa", help = "Creates a new JPA persistent entity in SRC_MAIN_JAVA")
    public void newPersistenceClassJpa(
            @CliOption(key = "class", optionContext = UPDATE_PROJECT, mandatory = true, help = "Name of the entity to create") final JavaType name,
            @CliOption(key = "extends", mandatory = false, unspecifiedDefaultValue = "java.lang.Object", optionContext = SUPERCLASS, help = "The superclass (defaults to java.lang.Object)") final JavaType superclass,
            @CliOption(key = "implements", mandatory = false, optionContext = INTERFACE, help = "The interface to implement") final JavaType implementsType,
            @CliOption(key = "abstract", mandatory = false, specifiedDefaultValue = "true", unspecifiedDefaultValue = "false", help = "Whether the generated class should be marked as abstract") final boolean createAbstract,
            @CliOption(key = "testAutomatically", mandatory = false, specifiedDefaultValue = "true", unspecifiedDefaultValue = "false", help = "Create automatic integration tests for this entity") final boolean testAutomatically,
            @CliOption(key = "table", mandatory = true, help = "The JPA table name to use for this entity") final String table,
            @CliOption(key = "schema", mandatory = false, help = "The JPA table schema name to use for this entity") final String schema,
            @CliOption(key = "catalog", mandatory = false, help = "The JPA table catalog name to use for this entity") final String catalog,
            @CliOption(key = "identifierField", mandatory = false, help = "The JPA identifier field name to use for this entity") final String identifierField,
            @CliOption(key = "identifierColumn", mandatory = true, help = "The JPA identifier field column to use for this entity") final String identifierColumn,
            @CliOption(key = "identifierType", mandatory = false, optionContext = "java-lang,project", unspecifiedDefaultValue = "java.lang.Long", specifiedDefaultValue = "java.lang.Long", help = "The data type that will be used for the JPA identifier field (defaults to java.lang.Long)") final JavaType identifierType,
            @CliOption(key = "versionField", mandatory = false, help = "The JPA version field name to use for this entity") final String versionField,
            @CliOption(key = "versionColumn", mandatory = false, help = "The JPA version field column to use for this entity") final String versionColumn,
            @CliOption(key = "versionType", mandatory = false, optionContext = "java-lang,project", unspecifiedDefaultValue = "java.lang.Integer", help = "The data type that will be used for the JPA version field (defaults to java.lang.Integer)") final JavaType versionType,
            @CliOption(key = "inheritanceType", mandatory = false, help = "The JPA @Inheritance value (apply to base class)") final InheritanceType inheritanceType,
            @CliOption(key = "mappedSuperclass", mandatory = false, specifiedDefaultValue = "true", unspecifiedDefaultValue = "false", help = "Apply @MappedSuperclass for this entity") final boolean mappedSuperclass,
            @CliOption(key = "equals", mandatory = false, unspecifiedDefaultValue = "false", specifiedDefaultValue = "true", help = "Whether the generated class should implement equals and hashCode methods") final boolean equals,
            @CliOption(key = "serializable", mandatory = false, unspecifiedDefaultValue = "false", specifiedDefaultValue = "true", help = "Whether the generated class should implement java.io.Serializable") final boolean serializable,
            @CliOption(key = "permitReservedWords", mandatory = false, unspecifiedDefaultValue = "false", specifiedDefaultValue = "true", help = "Indicates whether reserved words are ignored by Roo") final boolean permitReservedWords,
            @CliOption(key = "entityName", mandatory = false, help = "The name used to refer to the entity in queries") final String entityName,
            @CliOption(key = "sequenceName", mandatory = true, help = "The name of the sequence for incrementing sequence-driven primary keys") final String sequenceName,
            @CliOption(key = "readOnly", mandatory = false, unspecifiedDefaultValue = "false", specifiedDefaultValue = "true", help = "Whether the generated entity should be used for read operations only.") final boolean readOnly,
            ShellContext shellContext) {
        Validate.isTrue(!identifierType.isPrimitive(),
                "Identifier type cannot be a primitive");

        // Check if exists other entity with the same name
        Set<ClassOrInterfaceTypeDetails> currentEntities = typeLocationService
                .findClassesOrInterfaceDetailsWithAnnotation(ROO_JAVA_BEAN);

        for (ClassOrInterfaceTypeDetails entity : currentEntities) {
            // If exists and developer doesn't use --force global parameter,
            // we can't create a duplicate entity
            if (name.equals(entity.getName()) && !shellContext.isForce()) {
                throw new IllegalArgumentException(String.format(
                        "Entity '%s' already exists and cannot be created. Try to use a different entity name on --class parameter or use --force parameter to overwrite it.",
                        name));
            }
        }

        if (!permitReservedWords) {
            ReservedWords.verifyReservedWordsNotPresent(name);
        }
        
        if (testAutomatically && createAbstract) {
            // We can't test an abstract class
            throw new IllegalArgumentException(
                    "Automatic tests cannot be created for an abstract entity; remove the --testAutomatically or --abstract option");
        }

        // Reject attempts to name the entity "Test", due to possible clashes
        // with data on demand (see ROO-50)
        // We will allow this to happen, though if the user insists on it via
        // --permitReservedWords (see ROO-666)
        if (!BeanInfoUtils.isEntityReasonablyNamed(name)) {
            if (permitReservedWords && testAutomatically) {
                throw new IllegalArgumentException(
                        "Entity name cannot contain 'Test' or 'TestCase' as you are requesting tests; remove --testAutomatically or rename the proposed entity");
            }
            if (!permitReservedWords) {
                throw new IllegalArgumentException(
                        "Entity name rejected as conflicts with test execution defaults; please remove 'Test' and/or 'TestCase'");
            }
        }

        // Create entity's annotations
        final List<AnnotationMetadataBuilder> annotationBuilder = new ArrayList<AnnotationMetadataBuilder>();
        annotationBuilder.add(ROO_JAVA_BEAN_BUILDER);
        annotationBuilder.add(ROO_TO_STRING_BUILDER);
        annotationBuilder.add(getEntityAnnotationBuilder(table, schema, catalog,
                identifierField, identifierColumn, identifierType, versionField,
                versionColumn, versionType, inheritanceType, mappedSuperclass,
                entityName, sequenceName, readOnly));
        if (equals) {
            annotationBuilder.add(ROO_EQUALS_BUILDER);
        }
        if (serializable) {
            annotationBuilder.add(ROO_SERIALIZABLE_BUILDER);
        }

        // Produce the entity itself
        jpaOperations.newEntity(name, createAbstract, superclass,
                implementsType, annotationBuilder);

        // Create entity identifier class if required
        if (!(identifierType.getPackage().getFullyQualifiedPackageName()
                .startsWith("java.")
                || identifierType.equals(GAE_DATASTORE_KEY))) {
            jpaOperations.newIdentifier(identifierType, identifierField,
                    identifierColumn);
        }

        if (testAutomatically) {
            integrationTestOperations.newIntegrationTest(name);
        }
    }

    /**
     * Returns a builder for the entity-related annotation to be added to a
     * newly created JPA entity
     * 
     * @param table
     * @param schema
     * @param catalog
     * @param identifierField
     * @param identifierColumn
     * @param identifierType
     * @param versionField
     * @param versionColumn
     * @param versionType
     * @param inheritanceType
     * @param mappedSuperclass
     * @param entityName
     * @param sequenceName
     * @param readOnly
     * @return a non-<code>null</code> builder
     */
    private AnnotationMetadataBuilder getEntityAnnotationBuilder(
            final String table, final String schema, final String catalog,
            final String identifierField, final String identifierColumn,
            final JavaType identifierType, final String versionField,
            final String versionColumn, final JavaType versionType,
            final InheritanceType inheritanceType,
            final boolean mappedSuperclass, final String entityName,
            final String sequenceName, final boolean readOnly) {
        final AnnotationMetadataBuilder entityAnnotationBuilder = new AnnotationMetadataBuilder(
                ROO_JPA_ENTITY);

        // Attributes that apply to all JPA entities (active record or not)
        if (catalog != null) {
            entityAnnotationBuilder.addStringAttribute("catalog", catalog);
        }
        if (entityName != null) {
            entityAnnotationBuilder.addStringAttribute("entityName",
                    entityName);
        }
        if (sequenceName != null) {
            entityAnnotationBuilder.addStringAttribute("sequenceName",
                    sequenceName);
        }
        if (identifierColumn != null) {
            entityAnnotationBuilder.addStringAttribute("identifierColumn",
                    identifierColumn);
        }
        if (identifierField != null) {
            entityAnnotationBuilder.addStringAttribute("identifierField",
                    identifierField);
        }
        if (!LONG_OBJECT.equals(identifierType)) {
            entityAnnotationBuilder.addClassAttribute("identifierType",
                    identifierType);
        }
        if (inheritanceType != null) {
            entityAnnotationBuilder.addStringAttribute("inheritanceType",
                    inheritanceType.name());
        }
        if (mappedSuperclass) {
            entityAnnotationBuilder.addBooleanAttribute("mappedSuperclass",
                    mappedSuperclass);
        }
        if (schema != null) {
            entityAnnotationBuilder.addStringAttribute("schema", schema);
        }
        if (table != null) {
            entityAnnotationBuilder.addStringAttribute("table", table);
        }
        if (versionColumn != null
                && !RooJpaEntity.VERSION_COLUMN_DEFAULT.equals(versionColumn)) {
            entityAnnotationBuilder.addStringAttribute("versionColumn",
                    versionColumn);
        }
        if (versionField != null
                && !RooJpaEntity.VERSION_FIELD_DEFAULT.equals(versionField)) {
            entityAnnotationBuilder.addStringAttribute("versionField",
                    versionField);
        }
        if (!JavaType.INT_OBJECT.equals(versionType)) {
            entityAnnotationBuilder.addClassAttribute("versionType",
                    versionType);
        }
        
        // ROO-3708: Generate readOnly entities
        if(readOnly){
            entityAnnotationBuilder.addBooleanAttribute("readOnly", true);
        }

        return entityAnnotationBuilder;
    }

    private boolean isJdk6OrHigher() {
        final String ver = System.getProperty("java.version");
        return ver.indexOf("1.6.") > -1 || ver.indexOf("1.7.") > -1;
    }
}