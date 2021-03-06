/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.osgi.service;

import static org.jboss.as.server.Services.JBOSS_SERVICE_MODULE_LOADER;
import static org.jboss.as.server.moduleservice.ServiceModuleLoader.MODULE_SERVICE_PREFIX;
import static org.jboss.as.server.moduleservice.ServiceModuleLoader.MODULE_SPEC_SERVICE_PREFIX;

import java.util.List;

import org.jboss.as.server.moduleservice.ServiceModuleLoader;
import org.jboss.logging.Logger;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.msc.service.AbstractService;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.BundleManagerService;
import org.jboss.osgi.framework.ModuleLoaderProvider;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XModuleIdentity;
import org.jboss.osgi.spi.NotImplementedException;

/**
 * This is the single {@link ModuleLoader} that the OSGi layer uses for the modules that are associated with the bundles that
 * are registered with the {@link BundleManagerService}.
 *
 * Plain AS7 modules can create dependencies on OSGi deployments, because OSGi modules can also be loaded from the
 * {@link ServiceModuleLoader}
 *
 * @author thomas.diesler@jboss.com
 * @since 20-Apr-2011
 */
final class ModuleLoaderIntegration extends ModuleLoader implements ModuleLoaderProvider {

    // Provide logging
    private static final Logger log = Logger.getLogger("org.jboss.as.osgi");

    private final InjectedValue<ServiceModuleLoader> injectedModuleLoader = new InjectedValue<ServiceModuleLoader>();
    private ServiceContainer serviceContainer;
    private ServiceTarget serviceTarget;

    static void addService(final ServiceTarget target) {
        ModuleLoaderIntegration service = new ModuleLoaderIntegration();
        ServiceBuilder<?> builder = target.addService(Services.MODULE_LOADER_PROVIDER, service);
        builder.addDependency(JBOSS_SERVICE_MODULE_LOADER, ServiceModuleLoader.class, service.injectedModuleLoader);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private ModuleLoaderIntegration() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        log.debugf("Starting: %s", context.getController().getName());
        serviceContainer = context.getController().getServiceContainer();
        serviceTarget = context.getChildTarget();
    }

    @Override
    public void stop(StopContext context) {
        log.debugf("Stopping: %s", context.getController().getName());
    }

    @Override
    public ModuleLoaderProvider getValue() throws IllegalStateException {
        return this;
    }

    @Override
    public ModuleLoader getModuleLoader() {
        return this;
    }

    /**
     * Add a {@link ModuleSpec} for and OSGi module as a service that can later be looked up by the {@link ServiceModuleLoader}
     */
    @Override
    public void addModule(final ModuleSpec moduleSpec) {
        ModuleIdentifier identifier = moduleSpec.getModuleIdentifier();
        ServiceName serviceName = ServiceModuleLoader.moduleSpecServiceName(identifier);
        ServiceBuilder<ModuleSpec> builder = serviceTarget.addService(serviceName, new AbstractService<ModuleSpec>() {
            public ModuleSpec getValue() throws IllegalStateException {
                return moduleSpec;
            }
        });
        builder.install();
    }

    /**
     * Add an already loaded {@link Module} to the OSGi {@link ModuleLoader}. This happens when AS registers an existing
     * {@link Module} with the {@link BundleManagerService}.
     *
     * The {@link Module} may not necessarily result from a user deployment. We use the same {@link ServiceName} convention as
     * in {@link ServiceModuleLoader#moduleServiceName(ModuleIdentifier)}
     *
     * The {@link ServiceModuleLoader} cannot load these modules.
     */
    @Override
    public void addModule(final Module module) {
        ServiceName serviceName = getModuleServiceName(module.getIdentifier());
        if (serviceContainer.getService(serviceName) == null) {
            ServiceBuilder<Module> builder = serviceTarget.addService(serviceName, new AbstractService<Module>() {
                public Module getValue() throws IllegalStateException {
                    return module;
                }
            });
            builder.install();
        }
    }

    /**
     * Remove the {@link Module} and {@link ModuleSpec} services assocaiated with the given identifier.
     */
    @Override
    public void removeModule(ModuleIdentifier identifier) {
        ServiceName serviceName = getModuleSpecServiceName(identifier);
        ServiceController<?> controller = serviceContainer.getService(serviceName);
        if (controller != null) {
            controller.setMode(Mode.REMOVE);
        }
        serviceName = getModuleServiceName(identifier);
        controller = serviceContainer.getService(serviceName);
        if (controller != null) {
            controller.setMode(Mode.REMOVE);
        }
    }

    /**
     * Get the module identifier for the given {@link XModule}
     * The returned identifier must be such that it can be used
     * by the {@link ServiceModuleLoader}
     */
    @Override
    public ModuleIdentifier getModuleIdentifier(XModule resModule) {
        if (resModule == null)
            throw new IllegalArgumentException("Null resModule");

        XModuleIdentity moduleId = resModule.getModuleId();

        String slot = moduleId.getVersion().toString();
        int revision = moduleId.getRevision();
        if (revision > 0)
            slot += "-rev" + revision;

        String name = ServiceModuleLoader.MODULE_PREFIX + moduleId.getName();
        ModuleIdentifier identifier = ModuleIdentifier.create(name, slot);
        resModule.addAttachment(ModuleIdentifier.class, identifier);

        return identifier;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ModuleSpec findModule(ModuleIdentifier identifier) throws ModuleLoadException {
        ServiceName serviceName = ServiceModuleLoader.moduleSpecServiceName(identifier);
        ServiceController<ModuleSpec> controller = (ServiceController<ModuleSpec>) serviceContainer.getService(serviceName);
        return controller != null ? controller.getValue() : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Module preloadModule(ModuleIdentifier identifier) throws ModuleLoadException {
        ServiceName serviceName = getModuleServiceName(identifier);
        ServiceController<Module> controller = (ServiceController<Module>) serviceContainer.getService(serviceName);
        return controller != null ? controller.getValue() : ModuleLoader.preloadModule(identifier, injectedModuleLoader.getValue());
    }

    @Override
    public void setAndRelinkDependencies(Module module, List<DependencySpec> dependencies) throws ModuleLoadException {
        throw new NotImplementedException();
    }

    private ServiceName getModuleSpecServiceName(ModuleIdentifier identifier) {
        return MODULE_SPEC_SERVICE_PREFIX.append(identifier.getName()).append(identifier.getSlot());
    }

    private ServiceName getModuleServiceName(ModuleIdentifier identifier) {
        return MODULE_SERVICE_PREFIX.append(identifier.getName()).append(identifier.getSlot());
    }

    @Override
    public String toString() {
        return ModuleLoaderIntegration.class.getSimpleName();
    }
}