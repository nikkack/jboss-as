/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.connector.deployers.processors;

import org.jboss.as.connector.metadata.xmldescriptors.ConnectorXmlDescriptor;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ModuleDependency;
import org.jboss.as.server.deployment.module.ModuleSpecification;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;

public class RarDependencyProcessor implements DeploymentUnitProcessor {

    private static ModuleIdentifier JAVAX_ID = ModuleIdentifier.create("javax.resource.api");
    private static ModuleIdentifier JMS_ID = ModuleIdentifier.create("javax.jms.api");
    private static ModuleIdentifier LOGGING_ID = ModuleIdentifier.create("org.jboss.logging");
    private static ModuleIdentifier IRON_JACAMAR_ID = ModuleIdentifier.create("org.jboss.ironjacamar.api");
    private static ModuleIdentifier IRON_JACAMAR_IMPL_ID = ModuleIdentifier.create("org.jboss.ironjacamar.impl");
    private static ModuleIdentifier NAMING_ID = ModuleIdentifier.create("org.jboss.as.naming");
    private static ModuleIdentifier VALIDATION_ID = ModuleIdentifier.create("javax.validation.api");
    private static ModuleIdentifier HIBERNATE_VALIDATOR_ID = ModuleIdentifier.create("org.hibernate.validator");
    private static ModuleIdentifier COMMON_CORE_ID = ModuleIdentifier.create("org.jboss.common-core");
    private static ModuleIdentifier RESOURCE_API_ID = ModuleIdentifier.create("javax.resource.api");

    private static ModuleIdentifier SYSTEM_ID = ModuleIdentifier.create("javax.api");

    /**
     * Add dependencies for modules required for ra deployments
     * @param phaseContext the deployment unit context
     * @throws DeploymentUnitProcessingException
     */
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        if(phaseContext.getDeploymentUnit().getAttachment(ConnectorXmlDescriptor.ATTACHMENT_KEY) == null) {
            return;  // Skip non ra deployments
        }
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final ModuleLoader moduleLoader = Module.getBootModuleLoader();
        final ModuleSpecification moduleSpecification = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION);
        moduleSpecification.addDependency(new ModuleDependency(moduleLoader, JAVAX_ID, false, false, false));
        moduleSpecification.addDependency(new ModuleDependency(moduleLoader, JMS_ID, false, false, false));
        moduleSpecification.addDependency(new ModuleDependency(moduleLoader, LOGGING_ID, false, false, false));
        moduleSpecification.addDependency(new ModuleDependency(moduleLoader, IRON_JACAMAR_ID, false, false, false));
        moduleSpecification.addDependency(new ModuleDependency(moduleLoader, IRON_JACAMAR_IMPL_ID, false, true, false));
        moduleSpecification.addDependency(new ModuleDependency(moduleLoader, SYSTEM_ID, false, false, false));
        moduleSpecification.addDependency(new ModuleDependency(moduleLoader, NAMING_ID, false, false, false));
        moduleSpecification.addDependency(new ModuleDependency(moduleLoader, VALIDATION_ID, false, false, false));
        moduleSpecification.addDependency(new ModuleDependency(moduleLoader, HIBERNATE_VALIDATOR_ID, false, false, false));
        moduleSpecification.addDependency(new ModuleDependency(moduleLoader, COMMON_CORE_ID, false, false, false));
        moduleSpecification.addDependency(new ModuleDependency(moduleLoader, RESOURCE_API_ID, false, false, false));
    }

    public void undeploy(final DeploymentUnit context) {
    }
}
