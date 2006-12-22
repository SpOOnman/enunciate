package net.sf.enunciate.config;

import net.sf.enunciate.contract.validation.DefaultValidator;
import net.sf.enunciate.contract.validation.Validator;
import net.sf.enunciate.modules.DeploymentModule;
import org.apache.commons.digester.Digester;
import org.apache.commons.digester.Rule;
import org.apache.commons.digester.RuleSet;
import org.apache.commons.digester.parser.GenericParser;
import org.xml.sax.*;
import sun.misc.Service;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Base configuration object for enunciate.
 *
 * @author Ryan Heaton
 */
public class EnunciateConfiguration implements ErrorHandler {

  private String label = "enunciate";
  private Validator validator = new DefaultValidator();
  private final SortedSet<DeploymentModule> modules;
  private final Map<String, String> namespaces = new HashMap<String, String>();

  /**
   * Create a new enunciate configuration.  The module list will be constructed
   * using Sun's discovery mechanism.
   */
  public EnunciateConfiguration() {
    this.modules = new TreeSet<DeploymentModule>(new DeploymentModuleComparator());

    Iterator discoveredModules = Service.providers(DeploymentModule.class);
    while (discoveredModules.hasNext()) {
      DeploymentModule discoveredModule = (DeploymentModule) discoveredModules.next();
      this.modules.add(discoveredModule);
    }
  }

  /**
   * Construct an enunciate configuration with the specified set of modules.
   *
   * @param modules The modules.
   */
  public EnunciateConfiguration(Collection<DeploymentModule> modules) {
    this.modules = new TreeSet<DeploymentModule>(new DeploymentModuleComparator());
    this.modules.addAll(modules);
  }

  /**
   * The label for this enunciate project.
   *
   * @return The label for this enunciate project.
   */
  public String getLabel() {
    return label;
  }

  /**
   * The label for this enunciate project.
   *
   * @param label The label for this enunciate project.
   */
  public void setLabel(String label) {
    this.label = label;
  }

  /**
   * The configured validator, if any.
   *
   * @return The configured validator, or null if none.
   */
  public Validator getValidator() {
    return validator;
  }

  /**
   * The validator to use.
   *
   * @param validator The validator to use.
   */
  public void setValidator(Validator validator) {
    this.validator = validator;
  }

  /**
   * Configures a namespace for the specified prefix.
   *
   * @param namespace The namespace.
   * @param prefix    The prefix.
   */
  public void putNamespace(String namespace, String prefix) {
    this.namespaces.put(namespace, prefix);
  }

  /**
   * The map of namespaces to prefixes.
   *
   * @return The map of namespaces to prefixes.
   */
  public Map<String, String> getNamespacesToPrefixes() {
    return this.namespaces;
  }

  /**
   * The list of all deployment modules specified in the configuration.
   *
   * @return The list of all deployment modules specified in the configuration.
   */
  public SortedSet<DeploymentModule> getAllModules() {
    return modules;
  }

  /**
   * Add a module to the list of modules.
   *
   * @param module The module to add.
   */
  public void addModule(DeploymentModule module) {
    this.modules.add(module);
  }

  /**
   * The list of enabled modules in the configuration.
   *
   * @return The list of enabled modules in the configuration.
   */
  public List<DeploymentModule> getEnabledModules() {
    ArrayList<DeploymentModule> enabledModules = new ArrayList<DeploymentModule>();

    for (DeploymentModule module : getAllModules()) {
      if (!module.isDisabled()) {
        enabledModules.add(module);
      }
    }

    return enabledModules;
  }

  /**
   * Loads the configuration specified by the given config file.
   *
   * @param file The file.
   */
  public void load(File file) throws IOException, SAXException {
    load(new FileInputStream(file));
  }

  /**
   * Loads the configuration specified by the given stream.
   *
   * @param in The stream.
   */
  public void load(InputStream in) throws IOException, SAXException {
    Digester digester = createDigester();
    digester.setErrorHandler(this);
    digester.setValidating(true);
    digester.setSchema(EnunciateConfiguration.class.getResource("enunciate.xsd").toString());
    digester.push(this);

    //set any root-level attributes
    digester.addSetProperties("enunciate");

    //allow a validator to be configured.
    digester.addObjectCreate("enunciate/validator", "class", DefaultValidator.class);
    digester.addSetNext("enunciate/validator", "setValidator");

    //allow for namespace prefixes to be specified in the config file.
    digester.addCallMethod("enunciate/namespaces/namespace", "putNamespace", 2);
    digester.addCallParam("enunciate/namespaces/namespace", 0, "uri");
    digester.addCallParam("enunciate/namespaces/namespace", 1, "id");

    //set up the module configuration.
    for (DeploymentModule module : getAllModules()) {
      String pattern = String.format("enunciate/modules/%s", module.getName());
      digester.addRule(pattern, new PushModuleRule(module));
      digester.addSetProperties(pattern);
      RuleSet configRules = module.getConfigurationRules();
      if (configRules != null) {
        digester.addRuleSet(configRules);
      }
    }

    digester.parse(in);
  }

  /**
   * Create the digester.
   *
   * @return The digester that was created.
   */
  protected Digester createDigester() throws SAXException {
    try {
      SAXParserFactory factory = SAXParserFactory.newInstance();
      factory.setNamespaceAware(false);
      factory.setValidating(true);

      Properties properties = new Properties();
      properties.put("SAXParserFactory", factory);
      properties.put("schemaLocation", EnunciateConfiguration.class.getResource("enunciate.xsd").toString());
      properties.put("schemaLanguage", "http://www.w3.org/2001/XMLSchema");
      
      SAXParser parser = GenericParser.newSAXParser(properties);
      return new Digester(parser);
    }
    catch (ParserConfigurationException e) {
      throw new SAXException(e);
    }
  }

  /**
   * Handle a warning.
   *
   * @param warning The warning.
   */
  public void warning(SAXParseException warning) throws SAXException {
    System.err.println(warning.getMessage());
  }

  /**
   * Handle an error.
   *
   * @param error The error.
   */
  public void error(SAXParseException error) throws SAXException {
    throw error;
  }

  /**
   * Handle a fatal.
   *
   * @param fatal The fatal.
   */
  public void fatalError(SAXParseException fatal) throws SAXException {
    throw fatal;
  }

  /**
   * Rule to push a specific deployment module onto the digester stack.
   */
  private static class PushModuleRule extends Rule {

    private final DeploymentModule module;

    public PushModuleRule(DeploymentModule module) {
      this.module = module;
    }

    @Override
    public void begin(String namespace, String name, Attributes attributes) throws Exception {
      getDigester().push(module);
    }

    @Override
    public void end(String namespace, String name) throws Exception {
      getDigester().pop();
    }

  }

}