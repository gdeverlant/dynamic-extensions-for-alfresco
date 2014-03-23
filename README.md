Dynamic Extensions for Alfresco
===============================

Rapid development of Alfresco repository extensions in Java. Deploy your code in seconds, not minutes. Life is too short for endless server restarts.

Dynamic Extensions adds an OSGi container to the Alfresco repository, enabling live deployment of Java code, with no need to restart the server.
Alfresco itself is not "OSGi-fied" in any way; the OSGi container runs on top of the core Alfresco platform.

1.0.0 release
-------------

You can upgrade your extension project by updating the buildscript configuration:
```groovy
buildscript {
  repositories { maven { url 'https://raw.github.com/laurentvdl/dynamic-extensions-for-alfresco/mvn-repo/' } }
  dependencies {
    classpath group: 'com.github.dynamicextensionsalfresco', name: 'gradle-plugin', version: '1.0.0'
  }
}
```

Features:
* webscript resolutions: https://github.com/laurentvdl/dynamic-extensions-for-alfresco/wiki/Annotated-WebScripts#resolutions
* custom webscript ArgumentResolvers: https://github.com/laurentvdl/dynamic-extensions-for-alfresco/wiki/Annotated-WebScripts#attribute-providers-and-injection
* Activiti workflow support: extension Service Tasks: https://github.com/laurentvdl/dynamic-extensions-for-alfresco/wiki/Workflow
* automatic wrapping of non OSGi dependencies: https://github.com/laurentvdl/dynamic-extensions-for-alfresco/wiki/Extension-dependencies
* 2 amp distributions: Scala build is optional
* Abstract base class for serving static resources: https://github.com/laurentvdl/dynamic-extensions-for-alfresco/wiki/Annotated-WebScripts#serving-static-resources
* annotation support for exporting OSGi services: `@Component @OsgiService`

Breaking changes:

The support for Activiti breaks Alfresco 3.4 compatibility, so 4.0 is now the minimum required version.

Important note: due to the project ownership change, the base package structure has changed.
Replacing `nl.runnable.alfresco` with `com.github.dynamicextensionsalfresco` will make any previous extension compatible with the 1.0.0 release.


Installing Dynamic Extensions
-----------------------------

Dynamic Extensions is distributed as an Alfresco Module Package (AMP).

* Download the <a href="https://raw.github.com/laurentvdl/dynamic-extensions-for-alfresco/mvn-repo/com/github/dynamicextensionsalfresco/alfresco-module/1.0.0/com.github.dynamicextensionsalfresco-1.0.0.amp">Dynamic Extensions AMP</a>.
* For Scala based development, you can use the prepackaged Scala amp: <a href="https://raw.github.com/laurentvdl/dynamic-extensions-for-alfresco/mvn-repo/com/github/dynamicextensionsalfresco/alfresco-module/1.0.0/com.github.dynamicextensionsalfresco-scala-1.0.0.amp">Dynamic Extensions AMP for Scala</a>.
* Use the <a href="http://docs.alfresco.com/4.0/index.jsp?topic=%2Fcom.alfresco.enterprise.doc%2Ftasks%2Famp-install.html">Module Management Tool</a> to install the AMP in the Alfresco repository of your choosing.
* After restarting Alfresco, open the Control Panel: <a href="http://localhost:8080/alfresco/service/dynamic-extensions/">http://localhost:8080/alfresco/service/dynamic-extensions/</a>.
* Accessing the Control Panel requires an admin account.

Supported Alfresco versions
---------------------------

Dynamic Extensions is developed and tested against:

* Alfresco Enterprise 4.1
* Alfresco Enterprise 4.2

Dynamic Extensions is also known to work on:

* Alfresco Enterprise 4.0
* Alfresco Community 4.0
* Alfresco Community 4.2

Due to Activiti support, Alfresco 3.4 is not supported, you can use a 3.4 specific fork if you need support:

https://github.com/lfridael/dynamic-extensions-for-alfresco-3.4

Example extension code
----------------------

This example Web Script examines a node and passes information to a Freemarker template:
```java
@Component
@WebScript
public ExampleWebScript {

  @Autowired
  private NodeService nodeService;
  
  @Uri("/show-node")
  // Example: http://localhost/alfresco/service/show-node?nodeRef=workspace://SpacesStore/12345
  public Map<String, Object> displayNodeName(@RequestParam NodeRef nodeRef) {
    Map<String, Object> model = new HashMap<String, Object>();
    model.put("properties", nodeService.getProperties(nodeRef));    
    return model; // Model is passed to Freemarker template.
  }
}
```

Note that this is an _annotation Web Script_. These types of Web Script are configured through Java annotations instead of `*.desc.xml` descriptors. Annotation Web Scripts are similar to Spring MVC's annotation-based controllers.

Here's the accompanying Freemarker template fragment:

```html
<table>
  <#list properties?keys as name>    
    <tr>
      <th>${name}</th>
      <td>${properties[name]!''}</td>
    </tr>
  </#list>
</table>
```

This is all the code that is required; there's no need for Spring XML config or Web Script XML descriptors. Hot-reloading and reducing configuration overhead are not particularly novel concepts in the Java development world at large. Essentially, Dynamic Extensions modernizes the development of Alfresco repository extensions.

The example above may be trivial, but the point is that, behind the scenes, services are still wired together through Spring and handled by the Web Script framework. Conceptually there is no real difference between a Dynamic Extension and a regular Alfresco extension. There's just less overhead and more convenience.

Creating your own Alfresco extensions
-------------------------------------

The <a href="https://github.com/laurentvdl/example-dynamic-extension">example Dynamic Extension</a> provides a good starting point for creating your own extensions.

Clone the example repo and explore it. Here are some pointers to get you going:

* Annotation-based Web Scripts: <a href="https://github.com/laurentvdl/example-dynamic-extension/blob/master/src/main/java/com/github/dynamicextensionsalfresco/examples/CategoriesWebScript.java">First example</a> and <a href="https://github.com/laurentvdl/example-dynamic-extension/blob/master/src/main/java/com/github/dynamicextensionsalfresco/examples/HelloWebScript.java">another example</a>.
* <a href="https://github.com/laurentvdl/example-dynamic-extension/blob/master/src/main/java/com/github/dynamicextensionsalfresco/examples/ExampleActions.java">Annotation-based Action example</a >  and a <a href="https://github.com/laurentvdl/example-dynamic-extension/blob/master/src/main/java/com/github/dynamicextensionsalfresco/examples/SetDescriptionWebScript.java">Web Script</a> that invokes the action.
* <a href="https://github.com/laurentvdl/example-dynamic-extension/blob/master/src/main/java/com/github/dynamicextensionsalfresco/examples/ExampleBehaviour.java">Annotation-based Behaviour example</a>.
* <a href="https://github.com/laurentvdl/example-dynamic-extension/tree/master/src/main/java/com/github/dynamicextensionsalfresco/examples">All examples</a>.

See also: <a href="https://github.com/laurentvdl/dynamic-extensions-for-alfresco/wiki/Building-Alfresco-repository-extensions-for-Dynamic-Extensions-using-Gradle">Gradle plugin for building your own repository extensions</a>

Barcelona 2013 presentation
---------------------------

<a href="http://summit.alfresco.com/barcelona/sessions/life-too-short-endless-restarts">One hour presentation on using and exploring Dynamic Extensions for Alfresco</a>