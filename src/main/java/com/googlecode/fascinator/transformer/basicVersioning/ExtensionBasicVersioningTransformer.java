/* 
 * Fascinator - Plugin - Tranformer - Basic Versioning with File Extension support
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.io.FilenameUtils;
import org.json.simple.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.googlecode.fascinator.api.PluginDescription;
import com.googlecode.fascinator.api.PluginException;
import com.googlecode.fascinator.api.storage.DigitalObject;
import com.googlecode.fascinator.api.storage.Payload;
import com.googlecode.fascinator.api.storage.StorageException;
import com.googlecode.fascinator.api.transformer.TransformerException;
import com.googlecode.fascinator.common.JsonObject;
import com.googlecode.fascinator.common.JsonSimple;
import com.googlecode.fascinator.common.JsonSimpleConfig;
import com.googlecode.fascinator.common.storage.StorageUtils;

/**
 * An extension of the Basic versioning transformer plugin so as to support
 * backward compatibility of existing users.
 * 
 * This introduces sweeping changes in how the versions are recorded and stored.
 * 
 * @author Shilo Banihit
 *
 */

@Component(value = "extensionBasicVersioningTransformer")
public class ExtensionBasicVersioningTransformer extends
		BasicVersioningTransformer {
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

	/**
	 * Wrapping tranform calls without configuration path.
	 * 
	 * @param in
	 * @return
	 * @throws TransformerException
	 */
	public DigitalObject transform(DigitalObject in)
			throws TransformerException {
		try {
			systemConfig = new JsonSimpleConfig();
			return transform(
					in,
					new JsonSimple(JsonSimpleConfig.getSystemFile()).getObject(
							"transformerDefaults", getId()).toString());
		} catch (IOException e) {
			throw new TransformerException(e);
		}
	}

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
				log.debug("Looking for " + source + " in " + payloadId);
				if (payloadId.endsWith(source)) {
					source = payloadId;
				}
			}
			if (source.startsWith(".")) {
				log.info(
						"Versioning - Source not found at this time:'{}' from OID '{}'",
						source, in.getId());
				return in;
			}
			log.info("Versioning - Transforming PID '{}' from OID '{}'",
					source, in.getId());

			sourcePayload = in.getPayload(source);
		} catch (StorageException ex) {
			log.error("Error accessing payload in storage: '{}'", ex);
		}

		try {
			String payloadName = payloadName(source);
			try {
				in.createStoredPayload(payloadName, sourcePayload.open());
				sourcePayload.close();
				createVersionIndex(in, payloadName, source);
				return in;
			} catch (StorageException ex) {
				in.updatePayload(payloadName, sourcePayload.open());
				sourcePayload.close();
				createVersionIndex(in, payloadName, source);
				return in;
			}
		} catch (StorageException ex) {
			throw new TransformerException("Error storing payload: ", ex);
		}

	}

	private String payloadName(String source) {
		// Use a timestamp for the filename
		// DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
		// return "version_" + dateFormat.format(new Date());
		String ext = FilenameUtils.getExtension(source);
		return "version_" + ext + "_" + getTimestamp();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.googlecode.fascinator.api.Plugin#getId()
	 */
	@Override
	public String getId() {
		return "extensionBasicVersioning";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.googlecode.fascinator.api.Plugin#getName()
	 */
	@Override
	public String getName() {
		return "Basic versioning transformer with Extensions support";
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

	private void createVersionIndex(DigitalObject in, String payloadName,
			String source) {
		String versionIndexPayloadId = FilenameUtils.getExtension(source)
				+ "_Version_Index.json";

		JSONArray jArr = null;
		try {
			log.debug("Need to update a version index file: " + in.getId());
			Payload payload = in.getPayload(versionIndexPayloadId);
			JsonSimple js = new JsonSimple(payload.open());
			jArr = js.getJsonArray();
		} catch (Exception e) {
			log.debug("Need to create a new version index file: " + in.getId());
			jArr = new JSONArray();
		}

		String timestamp = getTimestamp();
		for (Object curEntryObj : jArr) {
			JsonObject curEntry = (JsonObject) curEntryObj;
			String curTs = (String) curEntry.get("timestamp");
			if (timestamp.equalsIgnoreCase(curTs)) {
				log.debug("A duplicate of the timestamp " + timestamp
						+ " is already found in the version index, ignoring.");
				return;
			}
		}
		JsonObject newVer = new JsonObject();
		newVer.put("timestamp", timestamp);
		newVer.put("file_name", payloadName); // adding the payloadName as a
												// parameter in the event that
												// calls between payloadname()
												// takes too long
		try {
			jArr.add(newVer);
			StorageUtils.createOrUpdatePayload(
					in,
					versionIndexPayloadId,
					new ByteArrayInputStream(jArr.toJSONString().getBytes(
							StandardCharsets.UTF_8)));
		} catch (Exception eStrange) {
			log.error("Failed to add a new version.", eStrange);
		}

	}
}
