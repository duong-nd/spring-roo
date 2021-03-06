package org.springframework.roo.project.settings;

import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.Validate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.springframework.roo.support.logging.HandlerUtils;

/**
 * Implementation of {@link ProjectSettingsOperations}.
 *
 * @author Paula Navarro
 * @author Juan Carlos García
 * @since 2.0
 */
@Component
@Service
public class ProjectSettingsOperationsImpl implements ProjectSettingsOperations {

	protected final static Logger LOGGER = HandlerUtils.getLogger(ProjectSettingsOperationsImpl.class);

	ProjectSettingsService projectSettingsService;

	// ------------ OSGi component attributes ----------------
	private BundleContext context;

	protected void activate(final ComponentContext context) {
		this.context = context.getBundleContext();
	}

	@Override
	public void addSetting(String name, String value, boolean force) {
		Validate.notNull(name, "Name required");
		Validate.notNull(value, "Value required");
		
		// Checks if project settings file exists
		if (!getProjectSettingsService().existsProjectSettingsFile()) {
			// Creates project settings file
			getProjectSettingsService().createProjectSettingsFile();
		}
		
		// Adds a setting
		getProjectSettingsService().addProperty(name, value, force);
	}
	
    @Override
    public void removeSetting(String name) {
        Validate.notNull(name, "Name required");
        
        // Checks if project settings file exists
        if (getProjectSettingsService().existsProjectSettingsFile()) {
            // Remove setting
            getProjectSettingsService().removeProperty(name);
        }else{
            LOGGER.log(Level.INFO,
                    "WARNING: Project settings file not found. Use 'project settings add' command to configure your project.");
        }
    }

	@Override
	public void listSettings() {
		// Checks if project settings file exists
		if (getProjectSettingsService().existsProjectSettingsFile()) {
		    
			Map<String, String> properties = getProjectSettingsService().getProperties();
			if(properties.size() > 0){
			    
			    printHeader();
			    // Print results
			    for (Entry<String, String> property : properties.entrySet()) {
			        LOGGER.log(Level.INFO, property.getKey().concat("=").concat(property.getValue()));
			    }
			    printFooter();
			}

		} else {
			LOGGER.log(Level.INFO,
					"WARNING: Project settings file not found. Use 'project settings add' command to configure your project.");
		}

	}
	
    /**
     * Method that prints header of Spring Roo Configuration
     */
    public void printHeader() {
        String header = "#===============================================#\n"
                + "#      SPRING ROO CONFIGURATION PROPERTIES      #\n"
                + "#===============================================#\n";
        LOGGER.log(Level.INFO, header);
    }
    
    /**
     * Method that prints footer of Spring Roo Configuration
     */
    public void printFooter() {
        LOGGER.log(Level.INFO, "");
        String footer = "These properties will be taken in mind during project generation.\n"
                + "Use 'project settings add' command to define some Spring Roo Configuration "
                + "properties.";
        LOGGER.log(Level.INFO, footer);
    }
    
	public ProjectSettingsService getProjectSettingsService() {
		if (projectSettingsService == null) {
			// Get all Services implement ProjectSettingsServic interface
			try {
				ServiceReference<?>[] references = this.context
						.getAllServiceReferences(ProjectSettingsService.class.getName(), null);

				for (ServiceReference<?> ref : references) {
					return (ProjectSettingsService) this.context.getService(ref);
				}

				return null;

			} catch (InvalidSyntaxException e) {
				LOGGER.warning("Cannot load ProjectSettingsService on ProjectSettingsOperationsImpl.");
				return null;
			}
		} else {
			return projectSettingsService;
		}

	}

}