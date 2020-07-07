package de.onvif.soap;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.xml.soap.SOAPException;

import org.apache.commons.codec.binary.Base64;
import org.onvif.ver10.device.wsdl.GetDeviceInformationResponse;
import org.onvif.ver10.schema.Capabilities;

import de.onvif.log.Logger;
import de.onvif.soap.devices.ImagingDevices;
import de.onvif.soap.devices.InitialDevices;
import de.onvif.soap.devices.MediaDevices;
import de.onvif.soap.devices.PtzDevices;
import org.onvif.ver10.schema.Profile;

/**
 * 
 * @author Robin Dick
 * 
 */
public class OnvifDevice {
	private final String HOST_IP;
	private String originalIp;

	private boolean isProxy;

	private String username, password, nonce, utcTime;

	private String serverDeviceUri, serverPtzUri, serverMediaUri, serverImagingUri, serverEventsUri;

	private SOAP soap;

	private InitialDevices initialDevices;
	private PtzDevices ptzDevices;
	private MediaDevices mediaDevices;
	private ImagingDevices imagingDevices;

	private Logger logger;
	/**
	 * Initializes an Onvif device, e.g. a Network Video Transmitter (NVT) with
	 * logindata.
	 *
	 * @param hostIp
	 *            The IP address of your device, you can also add a port but
	 *            noch protocol (e.g. http://)
	 * @param user
	 *            Username you need to login
	 * @param password
	 *            User's password to login
	 * @param showLogger
	 *            <code>true</code>logger info need to show,else not show logger
	 * @throws ConnectException
	 *             Exception gets thrown, if device isn't accessible or invalid
	 *             and doesn't answer to SOAP messages
	 * @throws SOAPException
	 */
	public OnvifDevice(String hostIp, String user, String password, boolean showLogger) throws ConnectException, SOAPException {
		this.logger = new Logger();

		this.HOST_IP = hostIp;

		if (!isOnline()) {
			throw new ConnectException("Host not available.");
		}

		this.serverDeviceUri = "http://" + HOST_IP + "/onvif/device_service";

		this.username = user;
		this.password = password;

		this.soap = new SOAP(this);
		this.soap.setLogging(showLogger);
		this.initialDevices = new InitialDevices(this);
		this.ptzDevices = new PtzDevices(this);
		this.mediaDevices = new MediaDevices(this);
		this.imagingDevices = new ImagingDevices(this);

		init();
	}

	/**
	 * Initializes an Onvif device, e.g. a Network Video Transmitter (NVT) with
	 * logindata.
	 * 
	 * @param hostIp
	 *            The IP address of your device, you can also add a port but
	 *            noch protocol (e.g. http://)
	 * @param user
	 *            Username you need to login
	 * @param password
	 *            User's password to login
	 * @throws ConnectException
	 *             Exception gets thrown, if device isn't accessible or invalid
	 *             and doesn't answer to SOAP messages
	 * @throws SOAPException 
	 */
	public OnvifDevice(String hostIp, String user, String password) throws ConnectException, SOAPException {
		this.logger = new Logger();

		this.HOST_IP = hostIp;

		if (!isOnline()) {
			throw new ConnectException("Host not available.");
		}

		this.serverDeviceUri = "http://" + HOST_IP + "/onvif/device_service";

		this.username = user;
		this.password = password;

		this.soap = new SOAP(this);
		this.initialDevices = new InitialDevices(this);
		this.ptzDevices = new PtzDevices(this);
		this.mediaDevices = new MediaDevices(this);
		this.imagingDevices = new ImagingDevices(this);
		
		init();
	}

	/**
	 * Initializes an Onvif device, e.g. a Network Video Transmitter (NVT) with
	 * logindata.
	 * 
	 * @param hostIp
	 *            The IP address of your device, you can also add a port but
	 *            noch protocol (e.g. http://)
	 * @throws ConnectException
	 *             Exception gets thrown, if device isn't accessible or invalid
	 *             and doesn't answer to SOAP messages
	 * @throws SOAPException 
	 */
	public OnvifDevice(String hostIp) throws ConnectException, SOAPException {
		this(hostIp, null, null);
	}

	/**
	 * Internal function to check, if device is available and answers to ping
	 * requests.
	 */
	private boolean isOnline() {
		String port = HOST_IP.contains(":") ? HOST_IP.substring(HOST_IP.indexOf(':') + 1) : "80";
		String ip = HOST_IP.contains(":") ? HOST_IP.substring(0, HOST_IP.indexOf(':')) : HOST_IP;
		
		Socket socket = null;
		try {
			SocketAddress sockaddr = new InetSocketAddress(ip, new Integer(port));
			socket = new Socket();

			socket.connect(sockaddr, 5000);
		}
		catch (NumberFormatException | IOException e) {
			return false;
		}
		finally {
			try {
				if (socket != null) {
					socket.close();
				}
			}
			catch (IOException ex) {
			}
		}
		return true;
	}

	/**
	 * Initalizes the addresses used for SOAP messages and to get the internal
	 * IP, if given IP is a proxy.
	 * 
	 * @throws ConnectException
	 *             Get thrown if device doesn't give answers to
	 *             GetCapabilities()
	 * @throws SOAPException 
	 */
	protected void init() throws ConnectException, SOAPException {
		Capabilities capabilities = getDevices().getCapabilities();

		if (capabilities == null) {
			throw new ConnectException("Capabilities not reachable.");
		}

		String localDeviceUri = capabilities.getDevice().getXAddr();

		if (localDeviceUri.startsWith("http://")) {
			originalIp = localDeviceUri.replace("http://", "");
			originalIp = originalIp.substring(0, originalIp.indexOf('/'));
		}
		else {
			logger.error("Unknown/Not implemented local procotol!");
		}
			
		if (!originalIp.equals(HOST_IP)) {
			isProxy = true;
		}

		if (capabilities.getMedia() != null && capabilities.getMedia().getXAddr() != null) {
			serverMediaUri = replaceLocalIpWithProxyIp(capabilities.getMedia().getXAddr());
		}

		if (capabilities.getPTZ() != null && capabilities.getPTZ().getXAddr() != null) {
			serverPtzUri = replaceLocalIpWithProxyIp(capabilities.getPTZ().getXAddr());
		}
		
		if (capabilities.getImaging() != null && capabilities.getImaging().getXAddr() != null) {
			serverImagingUri = replaceLocalIpWithProxyIp(capabilities.getImaging().getXAddr());
		}

		if (capabilities.getMedia() != null && capabilities.getEvents().getXAddr() != null) {
			serverEventsUri = replaceLocalIpWithProxyIp(capabilities.getEvents().getXAddr());
		}
	}

	public String replaceLocalIpWithProxyIp(String original) {
		if (original.startsWith("http:///")) {
			original.replace("http:///", "http://"+HOST_IP);
		}
		
		if (isProxy) {
			return original.replace(originalIp, HOST_IP);
		}
		return original;
	}

	public String getUsername() {
		return username;
	}

	public String getEncryptedPassword() {
		return encryptPassword();
	}

	/**
	 * Returns encrypted version of given password like algorithm like in WS-UsernameToken
	 */
	public String encryptPassword() {
		String nonce = getNonce();
		String timestamp = getUTCTime();

		String beforeEncryption = nonce + timestamp + password;

		byte[] encryptedRaw;
		try {
			encryptedRaw = sha1(beforeEncryption);
		}
		catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return null;
		}
		String encoded = Base64.encodeBase64String(encryptedRaw);
		return encoded;
	}

	private static byte[] sha1(String s) throws NoSuchAlgorithmException {
		MessageDigest SHA1 = null;
		SHA1 = MessageDigest.getInstance("SHA1");

		SHA1.reset();
		SHA1.update(s.getBytes());

		byte[] encryptedRaw = SHA1.digest();
		return encryptedRaw;
	}

	private String getNonce() {
		if (nonce == null) {
			createNonce();
		}
		return nonce;
	}

	public String getEncryptedNonce() {
		if (nonce == null) {
			createNonce();
		}
		return Base64.encodeBase64String(nonce.getBytes());
	}

	public void createNonce() {
		Random generator = new Random();
		nonce = "" + generator.nextInt();
	}

	public String getLastUTCTime() {
		return utcTime;
	}

	public String getUTCTime() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-d'T'HH:mm:ss'Z'");
		sdf.setTimeZone(new SimpleTimeZone(SimpleTimeZone.UTC_TIME, "UTC"));

		Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
		String utcTime = sdf.format(cal.getTime());
		this.utcTime = utcTime;
		return utcTime;
	}

	public SOAP getSoap() {
		return soap;
	}

	/**
	 * Is used for basic devices and requests of given Onvif Device
	 */
	public InitialDevices getDevices() {
		return initialDevices;
	}

	/**
	 * Can be used for PTZ controlling requests, may not be supported by device!
	 */
	public PtzDevices getPtz() {
		return ptzDevices;
	}

	/**
	 * Can be used to get media data from OnvifDevice
	 * @return
	 */
	public MediaDevices getMedia() {
		return mediaDevices;
	}

	/**
	 * Can be used to get media data from OnvifDevice
	 * @return
	 */
	public ImagingDevices getImaging() {
		return imagingDevices;
	}

	public Logger getLogger() {
		return logger;
	}

	public String getDeviceUri() {
		return serverDeviceUri;
	}

	protected String getPtzUri() {
		return serverPtzUri;
	}

	protected String getMediaUri() {
		return serverMediaUri;
	}

	protected String getImagingUri() {
		return serverImagingUri;
	}

	protected String getEventsUri() {
		return serverEventsUri;
	}
	
	public Date getDate() {
		return initialDevices.getDate();
	}
	
	public String getName() {
		return initialDevices.getDeviceInformation().getModel();
	}
	
	public String getHostname() {
		return initialDevices.getHostname();
	}
	
	public String reboot() throws ConnectException, SOAPException {
		return initialDevices.reboot();
	}
	public List<Profile> getProfiles(){
		return getDevices().getProfiles();
	}
	public String getProfileToken(int index){
		return getDevices().getProfiles().get(index).getToken();
	}

	public String getStreamUri() throws SOAPException, ConnectException {
		return getStreamUri(0);
	}
	public String getStreamUri(int index) throws SOAPException, ConnectException {
		return getMedia().getRTSPStreamUri(getProfileToken(0));
	}
	public String getSnapshotUri() throws SOAPException, ConnectException {
		return  getSnapshotUri(0);
	}
	public String getSnapshotUri(int index) throws ConnectException, SOAPException {
		if (getProfiles().size() >= index)
			return getMedia().getSnapshotUri(getProfileToken(index));
		return "";
	}

	public GetDeviceInformationResponse getInformation(){
		return getDevices().getDeviceInformation();
	}
}
