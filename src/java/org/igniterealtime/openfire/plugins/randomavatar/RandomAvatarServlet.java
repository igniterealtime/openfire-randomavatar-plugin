/*
 * Copyright (C) 2019 Ignite Realtime Foundation. All rights reserved.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Responsible for processing HTTP requests for avatars.
 *
 * From all available avatars, one is returned based on the path of the request URL. The same avatar is
 * returned in responses to requests that use the same path.
 *
 * The implementation allows for avatars to be choosen from certain subsets of avatars, called 'collections'.
 * This behavior is controlled by matching the first segment of the path against a collection name. If this
 * segment matches a known segment, only that collection will be used when choosing an avatar to be included
 * in the response.
 *
 * This implementation aims to return an avatar whenever possible: a request for an unknown collection is
 * therefor handled as if it were a request that didn't specify a specific collection.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class RandomAvatarServlet extends HttpServlet
{
    private static final Logger Log = LoggerFactory.getLogger( RandomAvatarServlet.class );

    private List<Path> indexedSourceFiles;

    @Override
    public void init( final ServletConfig config ) throws ServletException
    {
        super.init( config );

        final Path pluginDirectory = (Path) config.getServletContext().getAttribute( "org.igniterealtime.openfire.plugins.randomavatar.plugindirectory" );
        try
        {
            indexedSourceFiles = getIndexedSourcefiles( pluginDirectory );
        }
        catch ( IOException e )
        {
            Log.error( "An exception occurred wile trying to retrieve the indexed source files.", e );
            throw new ServletException( "Unable to initialize servlet: could not retrieve indexed files." );
        }
    }

    public static List<Path> getIndexedSourcefiles( Path pluginDirectory ) throws IOException
    {
        final Path imagePath = pluginDirectory.resolve( "classes" ).resolve( "images" );

        Log.info( "Using files from {}", imagePath );

        return listFileTree( imagePath );
    }

    public static List<Path> listFileTree( Path dir ) throws IOException
    {
        List<Path> fileTree = new ArrayList<>();

        final List<Path> content = Files.list( dir )
            .sorted()
            .collect( Collectors.toList() );

        for ( Path entry : content )
        {
            if ( Files.isDirectory( entry ) )
            {
                fileTree.addAll( listFileTree( entry ) );
            }
            else
            {
                if ( Files.isRegularFile( entry ) && entry.toString().toLowerCase().endsWith( ".svg" ) )
                {
                    fileTree.add( entry );
                }
            }
        }
        return fileTree;
    }

    public static List<Path> filter( List<Path> allFiles, String prefix )
    {
        final int secondSlash = prefix.substring( 1 ).indexOf( '/' ) + 1;
        if ( secondSlash <= 1 )
        {
            return allFiles;
        }
        final String collectionName = prefix.substring( 1, secondSlash );

        final List<Path> collection = allFiles.stream()
            .filter( p -> p.getParent().endsWith( collectionName ) )
            .sorted()
            .collect( Collectors.toList() );

        // If no specific collection can be found, use the entire set of files to pick a random image from.
        if ( collection.isEmpty() )
        {
            return allFiles;
        }
        else
        {
            return collection;
        }
    }

    public void doGet( HttpServletRequest request, HttpServletResponse response ) throws ServletException, IOException
    {
        Log.trace( "Processing doGet()" );

        final String seed = request.getPathInfo();

        final List<Path> collection = filter( indexedSourceFiles, seed );
        Log.debug( "Filtered original collection of size {} with seed {}, resulting in a collection of size {}", new Object[]{ indexedSourceFiles.size(), seed, collection.size() } );

        if ( collection.isEmpty() )
        {
            response.sendError( 404 );
            return;
        }

        final int index = Math.abs( seed.hashCode() ) % collection.size();
        final Path path = collection.get( index );
        Log.debug( "Calculated index {} from seed {}, which refers to file {}", new Object[]{ index, seed, path } );

        response.setContentType( "image/svg+xml" );
        response.setContentLengthLong( Files.size( path ) );
        response.setHeader( "Cache-Control", "max-age=31536000" );
        response.setHeader( "ETag", String.valueOf( path.hashCode() + Files.getLastModifiedTime( path ).hashCode() ) );

        try ( final InputStream in = Files.newInputStream( path );
              final OutputStream out = response.getOutputStream() )
        {
            final byte[] buffer = new byte[1024 * 4];
            int bytesRead;
            while ( (bytesRead = in.read( buffer )) != -1 )
            {
                out.write( buffer, 0, bytesRead );
            }
        }
    }
}
