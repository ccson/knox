/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.gateway.topology.file;

import org.apache.commons.digester3.Digester;
import org.apache.commons.digester3.binder.DigesterLoader;
import org.apache.commons.vfs2.FileChangeEvent;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileListener;
import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.impl.DefaultFileMonitor;
import org.apache.hadoop.gateway.topology.ClusterTopology;
import org.apache.hadoop.gateway.topology.ClusterTopologyEvent;
import org.apache.hadoop.gateway.topology.ClusterTopologyListener;
import org.apache.hadoop.gateway.topology.ClusterTopologyMonitor;
import org.apache.hadoop.gateway.topology.ClusterTopologyProvider;
import org.apache.hadoop.gateway.topology.xml.ClusterTopologyRulesModule;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.apache.commons.digester3.binder.DigesterLoader.newLoader;

public class FileClusterTopologyProvider implements ClusterTopologyProvider, ClusterTopologyMonitor, FileListener {

  private static DigesterLoader digesterLoader = newLoader( new ClusterTopologyRulesModule() );

  private FileSystemManager manager;
  private DefaultFileMonitor monitor;
  private FileObject directory;
  private Set<ClusterTopologyListener> listeners;
  private volatile Map<FileName, ClusterTopology> topology;

  // For unit testing.
  FileClusterTopologyProvider( DefaultFileMonitor monitor, String directory ) throws IOException, SAXException {
    this.manager = VFS.getManager();
    this.directory = this.manager.resolveFile( directory );
    this.monitor = ( monitor != null ) ? monitor : new DefaultFileMonitor( this );
    this.monitor.setRecursive( true );
    this.monitor.addFile( this.directory );
    this.listeners = new HashSet<ClusterTopologyListener>();
    this.topology = loadTopologies( this.directory );
  }

  public FileClusterTopologyProvider( String directory ) throws IOException, SAXException {
    this( null, directory );
  }

  private static ClusterTopology loadTopology( FileObject file ) throws IOException, SAXException {
    Digester digester = digesterLoader.newDigester();
    FileContent content = file.getContent();
    ClusterTopology topology = digester.parse( content.getInputStream() );
    topology.setTimestamp( content.getLastModifiedTime() );
    return topology;
  }

  private static Map<FileName, ClusterTopology> loadTopologies( FileObject directory ) throws FileSystemException {
    Map<FileName, ClusterTopology> map = new HashMap<FileName, ClusterTopology>();
    if( directory.exists() && directory.getType().hasChildren() ) {
      for( FileObject file : directory.getChildren() ) {
        try {
          map.put( file.getName(), loadTopology( file ) );
        } catch( IOException e ) {
          e.printStackTrace();
        } catch( SAXException e ) {
          e.printStackTrace();
        }
      }
    }
    return map;
  }

  private void reloadTopologies() throws FileSystemException {
    synchronized ( this ) {
      Map<FileName, ClusterTopology> oldTopologies = topology;
      Map<FileName, ClusterTopology> newTopologies = loadTopologies( directory );
      List<ClusterTopologyEvent> events = createChangeEvents( oldTopologies, newTopologies );
      topology = newTopologies;
      notifyChangeListeners( events );
    }
  }

  private static List<ClusterTopologyEvent> createChangeEvents(
    Map<FileName, ClusterTopology> oldTopologies,
    Map<FileName, ClusterTopology> newTopologies ) {
    ArrayList<ClusterTopologyEvent> events = new ArrayList<ClusterTopologyEvent>();
    // Go through the old topologies and find anything that was deleted.
    for( FileName fileName : oldTopologies.keySet() ) {
      if( !newTopologies.containsKey( fileName ) ) {
        events.add( new ClusterTopologyEvent( ClusterTopologyEvent.Type.DELETED, oldTopologies.get( fileName ) ) );
      }
    }
    // Go through the new topologies and figure out what was updated vs added.
    for( FileName fileName : newTopologies.keySet() ) {
      if( oldTopologies.containsKey( fileName ) ) {
        ClusterTopology oldTopology = oldTopologies.get( fileName );
        ClusterTopology newTopology = newTopologies.get( fileName );
        if( newTopology.getTimestamp() > oldTopology.getTimestamp() ) {
          events.add( new ClusterTopologyEvent( ClusterTopologyEvent.Type.UPDATED, newTopologies.get( fileName ) ) );
        }
      } else {
        events.add( new ClusterTopologyEvent( ClusterTopologyEvent.Type.CREATED, newTopologies.get( fileName ) ) );
      }
    }
    return events ;
  }

  private void notifyChangeListeners( List<ClusterTopologyEvent> events ) {
    for( ClusterTopologyListener listener : listeners ) {
      try {
        listener.handleTopologyChangeEvent( events );
      } catch( RuntimeException e ) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public Collection<ClusterTopology> getClusterTopologies() {
    Map<FileName, ClusterTopology> map = topology;
    return Collections.unmodifiableCollection( map.values() );
  }

  @Override
  public void addTopologyChangeListener( ClusterTopologyListener listener ) {
    listeners.add( listener );
  }

  @Override
  public void startMonitor() {
    monitor.start();
  }

  @Override
  public void stopMonitor() {
    monitor.stop();
  }

  @Override
  public void fileCreated( FileChangeEvent fileChangeEvent ) throws Exception {
    reloadTopologies();
  }

  @Override
  public void fileDeleted( FileChangeEvent fileChangeEvent ) throws Exception {
    reloadTopologies();
  }

  @Override
  public void fileChanged( FileChangeEvent fileChangeEvent ) throws Exception {
    reloadTopologies();
  }

}