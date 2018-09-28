package com.github.dynamicextensionsalfresco.osgi

import com.github.dynamicextensionsalfresco.debug
import com.github.dynamicextensionsalfresco.info
import com.github.dynamicextensionsalfresco.warn
import org.alfresco.model.ContentModel
import org.alfresco.service.cmr.repository.ContentService
import org.osgi.framework.Bundle
import org.osgi.framework.BundleException
import org.osgi.framework.Constants
import org.osgi.framework.ServiceRegistration
import org.osgi.framework.launch.Framework
import org.osgi.framework.wiring.FrameworkWiring
import org.slf4j.LoggerFactory
import org.springframework.context.ResourceLoaderAware
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.core.io.support.ResourcePatternResolver
import org.springframework.stereotype.Service
import org.springframework.util.Assert
import org.springframework.util.StringUtils
import java.io.FileNotFoundException
import java.io.IOException
import java.util.*

/**
 * Manages a [Framework]'s lifecycle. It taken care of initializing and destroying the Framework and
 * (un)registering services and [BundleListener]s.

 * @author Laurens Fridael
 */
interface FrameworkManager {
    val framework: Framework
}

@Service class DefaultFrameworkManager(
        override val framework: Framework,
        private val bundleContextRegistrars: List<BundleContextRegistrar>,
        private val repositoryStoreService: RepositoryStoreService,
        private val contentService: ContentService,
        private val configuration: Configuration,
        private val blueprintBundlesLocation: String,
        private val standardBundlesLocation: String,
        private val customBundlesLocation: String
) : ResourceLoaderAware, FrameworkManager {

    private val logger = LoggerFactory.getLogger(javaClass)

    private var resourcePatternResolver: ResourcePatternResolver? = null

    val serviceRegistrations = ArrayList<ServiceRegistration<*>>()

    /**
     * Starts the [Framework] and registers services and [BundleListener]s.

     * @throws BundleException
     */
    fun initialize() {
        startFramework()
        registerServices()
        startBundles(installCoreBundles())
        if (repositoryInstallEnabled) {
            startBundles(installRepositoryBundles())
        }
    }

    protected fun startFramework() {
        try {
            logger.debug("Starting Framework.")
            framework.start()
        } catch (e: BundleException) {
            logger.error("Could not start Framework.", e)
        }
    }

    /**
     * Installs the Bundles that make up the core of the framework. These bundles are started before any extension
     * bundles.
     *
     *
     * The core bundles consist of:
     *
     *  * Gemini Blueprint
     *  * File Install (optional, can be disabled)
     *  * Any additional standard bundles configured through [.setStandardBundlesLocation].
     *
     * @return installed bundles
     */
    protected fun installCoreBundles(): List<Bundle> {
        val bundles = ArrayList<Bundle>()
        try {
            val locationPatterns = ArrayList<String>()
            locationPatterns.add(blueprintBundlesLocation)
            if (StringUtils.hasText(standardBundlesLocation)) {
                locationPatterns.add(standardBundlesLocation)
            }
            if (StringUtils.hasText(customBundlesLocation)) {
                locationPatterns.add(customBundlesLocation)
            }
            for (locationPattern in locationPatterns) {
                try {
                    for (bundleResource in resourcePatternResolver!!.getResources(locationPattern)) {
                        val location = bundleResource.uri.toString()
                        logger.debug ("Installing Bundle: {}", location)
                        try {
                            val bundle = installBundle(bundleResource, location)
                            bundles.add(bundle)
                        } catch (e: BundleException) {
                            logger.error("Error installing Bundle: $location", e)
                        }

                    }
                } catch (e: FileNotFoundException) {
                    logger.debug("Could not find Bundles at location '{}'.", locationPattern)
                }

            }
        } catch (e: IOException) {
            throw RuntimeException("Error installing core Bundles: ${e.message}", e)
        }

        return bundles
    }

    /**
     * optimistic install: first install & if it turns out to be a regular jar, replace with wrapped jar
     */
    private fun installBundle(bundleResource: Resource, location: String): Bundle {
        var bundle = framework.bundleContext.installBundle(location, bundleResource.inputStream)
        if (bundle.symbolicName == null) {
            bundle.uninstall()
            val localCopy = bundleResource.inputStream.toTempFile("wrapped", ".jar")
            bundle = framework.bundleContext.installBundle(
                    location,
                    localCopy.convertToBundle(bundleResource.filename).inputStream()
            )
            logger.info { "Wrapped plain jar as a OSGi bundle: ${bundle.symbolicName}." }
        }
        return bundle
    }

    /**
     * Installs the Bundles in the repository.
     *
     *
     * This implementation uses RepositoryStoreService.
     */
    protected fun installRepositoryBundles(): List<Bundle> {
        val bundles = ArrayList<Bundle>()
        for (jarFile in repositoryStoreService.bundleJarFiles) {
            try {
                val location = "%s/%s".format(repositoryStoreService.bundleRepositoryLocation, jarFile.name)
                logger.debug("Installing Bundle: {}", location)
                val reader = contentService.getReader(jarFile.nodeRef, ContentModel.PROP_CONTENT)
                if (reader != null) {
                    val bundle = framework.bundleContext.installBundle(location, reader.contentInputStream)
                    bundles.add(bundle)
                } else {
                    logger.warn("unable to read extension content for ${jarFile?.nodeRef}")
                }
            } catch (e: Exception) {
                logger.warn("Error installing Bundle: ${jarFile?.nodeRef}", e)
            }

        }
        return bundles
    }

    protected fun registerServices() {
        logger.debug("Registering services.")
        for (bundleContextRegistrar in bundleContextRegistrars) {
            val servicesRegistered = bundleContextRegistrar.registerInBundleContext(framework.bundleContext)
            serviceRegistrations.addAll(servicesRegistered)
        }
    }

    protected fun startBundles(bundles: List<Bundle>) {
        val frameworkWiring = framework.adapt(FrameworkWiring::class.java)
        if (frameworkWiring.resolveBundles(bundles) == false) {
            logger.warn { "Could not resolve all ${bundles.size} bundles." }
        }

        val sortedByDependency = BundleDependencies.sortByDependencies(bundles)

        for (bundle in sortedByDependency) {
            if (isFragmentBundle(bundle) == false) {
                startBundle(bundle)
            }
        }
    }

    protected fun startBundle(bundle: Bundle) {
        try {
            logger.debug("Starting Bundle {}.", bundle.bundleId)
            bundle.start()
        } catch (e: Exception) {
            logger.error("Error starting Bundle ${bundle.bundleId}.", e)
        }

    }

    protected fun isFragmentBundle(bundle: Bundle): Boolean {
        return bundle.headers.get(Constants.FRAGMENT_HOST) != null
    }

    /**

     * Unregisters services and [BundleListener]s and stops the [Framework].
     */
    fun destroy() {
        unregisterServices()
        stopFramework()
    }

    protected fun unregisterServices() {
        val it = serviceRegistrations.iterator()
        while (it.hasNext()) {
            val serviceRegistration = it.next()
            try {
                logger.debug { "Unregistering service ${serviceRegistration.reference}" }
                serviceRegistration.unregister()
            } catch (e: RuntimeException) {
                logger.warn("Error unregistering service $serviceRegistration.", e)
            } finally {
                it.remove()
            }
        }
    }

    protected fun stopFramework() {
        try {
            logger.debug("Stopping Framework.")
            framework.stop()
            framework.waitForStop(0)
        } catch (e: BundleException) {
            logger.error("Could not stop Framework.", e)
        } catch (ignore: InterruptedException) {}

    }

    override fun setResourceLoader(resourceLoader: ResourceLoader) {
        Assert.isInstanceOf(ResourcePatternResolver::class.java, resourceLoader)
        this.resourcePatternResolver = resourceLoader as ResourcePatternResolver
    }

    val repositoryInstallEnabled: Boolean
        get() = configuration.repositoryBundlesEnabled

}
