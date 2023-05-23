/*
 * Copyright (C) 2019-2023 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.igniterealtime.openfire.plugins.randomavatar;

import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.SimpleInstanceManager;
import org.eclipse.jetty.apache.jsp.JettyJasperInitializer;
import org.eclipse.jetty.plus.annotation.ContainerInitializer;
import org.eclipse.jetty.webapp.WebAppContext;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.http.HttpBindManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * An Openfire plugin that makes available an avatar-exposing webservice.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class RandomAvatarPlugin implements Plugin
{
    /**
     * The context root of the URL under which the web client is exposed.
     */
    public static final String CONTEXT_ROOT = "randomavatar";

    private static final Logger Log = LoggerFactory.getLogger( RandomAvatarServlet.class );

    private WebAppContext context = null;

    @Override
    public void initializePlugin( PluginManager manager, File pluginDirectory )
    {
        Log.debug( "Plugin initialization started." );

        Log.debug( "Adding the Webchat sources to the same context as the one that's providing the BOSH interface." );
        context = new WebAppContext( null, pluginDirectory.getPath() + File.separator + "classes/", "/" + CONTEXT_ROOT );
        context.setClassLoader( this.getClass().getClassLoader() );

        Log.debug( "Ensure the JSP engine is initialized correctly (in order to be able to cope with Tomcat/Jasper precompiled JSPs)." );
        final List<ContainerInitializer> initializers = new ArrayList<>();
        initializers.add( new ContainerInitializer( new JettyJasperInitializer(), null ) );
        context.setAttribute( "org.eclipse.jetty.containerInitializers", initializers );
        context.setAttribute( InstanceManager.class.getName(), new SimpleInstanceManager() );
        context.setAttribute( "org.igniterealtime.openfire.plugins.randomavatar.plugindirectory", pluginDirectory.toPath() );
        Log.debug( "Registering context with the embedded webserver." );
        HttpBindManager.getInstance().addJettyHandler( context );

        Log.debug( "Plugin initialization finished." );
    }

    @Override
    public void destroyPlugin()
    {
        Log.debug( "Plugin destruction started." );
        if ( context != null )
        {
            Log.debug( "Removing context from the embedded webserver." );
            HttpBindManager.getInstance().removeJettyHandler( context );
            context.destroy();
            context = null;
        }

        Log.debug( "Plugin finished started." );
    }
}
