/* 
 * Fascinator - Plugin - Tranformer - Basic Versioning
 * Copyright (C) 2013 Queensland Cyber Infrastructure Foundation (http://www.qcif.edu.au/)
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package com.googlecode.fascinator.transformer.basicVersioning;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import java.io.FileWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.fascinator.api.PluginDescription;
import com.googlecode.fascinator.api.PluginException;
import com.googlecode.fascinator.api.storage.DigitalObject;
import com.googlecode.fascinator.api.storage.Payload;
import com.googlecode.fascinator.api.storage.StorageException;
import com.googlecode.fascinator.api.transformer.Transformer;
import com.googlecode.fascinator.api.transformer.TransformerException;
import org.json.simple.JSONArray;
import com.googlecode.fascinator.common.JsonObject;
import com.googlecode.fascinator.common.JsonSimple;
import com.googlecode.fascinator.common.JsonSimpleConfig;
import com.googlecode.fascinator.storage.filesystem.FileSystemDigitalObject;

/**
 * <p>
 * This plugin purely copies the current version of the digital object to a timestamped duplicate.
 * </p>
 * 
 * <h3>Configuration</h3>
 *
 * <p>Keep in mind that each data source can provide overriding configuration.
 * This transformer currently allows overrides on all fields (except 'id').
 * </p>
 *
 * <table border="1">
 * <tr>
 * <th>Option</th>
 * <th>Description</th>
 * <th>Required</th>
 * <th>Default</th>
 * </tr>
 * 
 * <tr>
 * <td>id</td>
 * <td>Id of the transformer</td>
 * <td><b>Yes</b></td>
 * <td>basicVersioning</td>
 * </tr>
 * 
 * <tr>
 * <td>sourcePayload</td>
 * <td>Source payload from which the object will be versioned.</td>
 * <td><b>No</b></td>
 * <td>object.tfpackage</td>
 * </tr>
 * </table>
 *
 * <h3>Examples</h3>
 * <ol>
 * <li>
 * Adding  Transformer to The Fascinator
 * 
 * <pre>
 * "basicVersioning": {
 *     "id" : "basicVersioning",
 *     "sourcePayload" : "object.tfpackage",
 * }
 * </pre>
 * 
 * </li>
 * </ol>
 * 
 * @author Duncan Dickinson
 * @author Jianfeng Li
 */
public class BasicVersioningTransformer implements Transformer {
	
	/** Default payload */
    private static String DEFAULT_PAYLOAD = "object.tfpackage";

	/** Source payload to be transformed **/
	private String systemPayload;

	/** Logger */
	private static Logger log = LoggerFactory
			.getLogger(BasicVersioningTransformer.class);
	
	/** Json config file **/
    private JsonSimpleConfig systemConfig;
    
	/** Json config file **/
	private JsonSimple itemConfig;

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.googlecode.fascinator.api.transformer.Transformer#transform(com.
	 * googlecode.fascinator.api.storage.DigitalObject, java.lang.String)
	 */
	@Override
	public DigitalObject transform(DigitalObject in, String config)
			throws TransformerException {

		try {
            itemConfig = new JsonSimple(config);
        } catch (IOException ex) {
            throw new TransformerException("Invalid configuration! '{}'", ex);
        }
		
		// What is our source payload (base system setting)
        systemPayload = systemConfig.getString(DEFAULT_PAYLOAD,
                "transformerDefaults", "jsonVelocity", "sourcePayload");

        // Source payload - local setting
        String source = itemConfig.getString(systemPayload, "sourcePayload");
        Payload sourcePayload = null;
        try {
            // Sometimes config will be just an extension eg. ".tfpackage"
            for (String payloadId : in.getPayloadIdList()) {
                if (payloadId.endsWith(source)) {
                    source = payloadId;
                }
            }
            log.info("Versioning - Transforming PID '{}' from OID '{}'", source, in.getId());
            log.info(((FileSystemDigitalObject)in).getPath());
            sourcePayload = in.getPayload(source);
        } catch (StorageException ex) {
            log.error("Error accessing payload in storage: '{}'", ex);
        }

        try {
        	String payloadName = payloadName();
            try {
                in.createStoredPayload(payloadName, sourcePayload.open());
                sourcePayload.close();
                createVersionIndex(((FileSystemDigitalObject)in).getPath());
                return in;
            } catch (StorageException ex) {
                in.updatePayload(payloadName, sourcePayload.open());
                sourcePayload.close();
                createVersionIndex(((FileSystemDigitalObject)in).getPath());
                return in;
            }
        } catch (StorageException ex) {
            throw new TransformerException("Error storing payload: ", ex);
        }

	}
	
	

    private String payloadName() {
		// Use a timestamp for the filename
		//DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
		//return "version_" + dateFormat.format(new Date());
		return "version_" + getTimestamp();
    }
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.googlecode.fascinator.api.Plugin#getId()
	 */
	@Override
	public String getId() {
		return "basicVersioning";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.googlecode.fascinator.api.Plugin#getName()
	 */
	@Override
	public String getName() {
		return "Basic versioning transformer";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.googlecode.fascinator.api.Plugin#getPluginDetails()
	 */
	@Override
	public PluginDescription getPluginDetails() {
		return new PluginDescription(this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.googlecode.fascinator.api.Plugin#init(java.io.File)
	 */
	@Override
	public void init(File jsonFile) throws PluginException {
		try {
            systemConfig = new JsonSimpleConfig(jsonFile);
        } catch (IOException e) {
            throw new PluginException(e);
        }

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.googlecode.fascinator.api.Plugin#init(java.lang.String)
	 */
	@Override
	public void init(String jsonString) throws PluginException {
        try {
            systemConfig = new JsonSimpleConfig(jsonString);
        } catch (IOException e) {
            throw new PluginException(e);
        }

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.googlecode.fascinator.api.Plugin#shutdown()
	 */
	@Override
	public void shutdown() throws PluginException {
		// No tidy up needed
	}

    private String getTimestamp() {
		DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
		return dateFormat.format(new Date());
    }

    private void createVersionIndex(String rootPath) {
        String jsonPath = rootPath + "/" + "Version_Index.json";
        log.debug("Indexing a version into: " + jsonPath);

        JSONArray jArr = null;
		try {
			File oldf = new File(jsonPath);
	        if (oldf.exists()) {
	            log.debug("Need to update a version index file: " + jsonPath);
	            JsonSimple js = new JsonSimple(oldf);
	        	jArr = js.getJsonArray();
	        } else {
	        	log.debug("Need to create a new version index file: " + jsonPath);
	        	jArr = new JSONArray();
	        }
	    	JsonObject newVer = new JsonObject();
	    	newVer.put("timestamp", getTimestamp() );
	    	newVer.put("file_name", payloadName() );
	    	try {
		    	jArr.add(newVer);
		        try {
			        FileWriter fw = new FileWriter(jsonPath);
		    		fw.write(jArr.toJSONString());
		    		fw.flush();
		    		fw.close();
		        } catch (IOException e) {
		        	log.error("Failed to save versioning property file.", e);
		        }
	    	} catch (Exception eStrange) {
	    		log.error("Failed to add a new version.", eStrange);
	    	}
		} catch (Exception eOther) {
			log.error("Failed to create/edit versioning property file.", eOther);
		}
    }
}
