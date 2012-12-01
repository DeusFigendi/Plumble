package com.morlunk.mumbleclient.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;

import org.spongycastle.asn1.x500.X500Name;
import org.spongycastle.asn1.x509.SubjectPublicKeyInfo;
import org.spongycastle.cert.X509CertificateHolder;
import org.spongycastle.cert.X509v3CertificateBuilder;
import org.spongycastle.cert.jcajce.JcaX509CertificateConverter;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.spongycastle.operator.ContentSigner;
import org.spongycastle.operator.OperatorCreationException;
import org.spongycastle.operator.jcajce.JcaContentSignerBuilder;

public class PlumbleCertificateManager {

	private static final String issuer = "CN=Plumble User";
	private static final Integer YEARS_VALID = 20;
	
	public static X509Certificate createCertificate(File path) throws NoSuchAlgorithmException, OperatorCreationException, CertificateException, KeyStoreException, NoSuchProviderException, IOException {
		BouncyCastleProvider provider = new BouncyCastleProvider(); // Use SpongyCastle provider, supports creating X509 certs
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048, new SecureRandom());
		
		KeyPair keyPair = generator.generateKeyPair();
		
		SubjectPublicKeyInfo publicKeyInfo = SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded());
		ContentSigner signer = new JcaContentSignerBuilder("SHA1withRSA").setProvider(provider).build(keyPair.getPrivate());
		
		Date startDate = new Date();
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(startDate);
		calendar.add(Calendar.YEAR, YEARS_VALID);
	    Date endDate = calendar.getTime();
		
		X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(new X500Name(issuer), 
				BigInteger.ONE, 
				startDate, endDate, new X500Name(issuer), 
				publicKeyInfo);
		
		X509CertificateHolder certificateHolder = certBuilder.build(signer);
		
		X509Certificate certificate = new JcaX509CertificateConverter().setProvider(provider).getCertificate(certificateHolder);
		
		KeyStore keyStore = KeyStore.getInstance("PKCS12", provider);
		keyStore.load(null, null);
		keyStore.setKeyEntry("Plumble Key", keyPair.getPrivate(), null, new X509Certificate[] { certificate });
		
		keyStore.store(new FileOutputStream(path), "".toCharArray());
		
		return certificate;
	}
}
