package sk.styk.martin.bakalarka.stats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import sk.styk.martin.bakalarka.data.ApkData;
import sk.styk.martin.bakalarka.data.CertificateData;
import sk.styk.martin.bakalarka.files.ApkFile;
import sk.styk.martin.bakalarka.files.FileFinder;
import sun.security.pkcs.PKCS7;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Martin Styk on 25.11.2015.
 */
public class CertificateProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CertificateProcessor.class);
    private Marker apkNameMarker;

    private static CertificateProcessor instance = null;
    private List<CertificateData> certDatas;
    private ApkData data;
    private ApkFile apkFile;

    private CertificateProcessor() {
        // Exists only to defeat instantiation.
    }

    public static CertificateProcessor getInstance(ApkData data, ApkFile apkFile) {
        if (data == null) {
            throw new IllegalArgumentException("data null");
        }
        if (apkFile == null) {
            throw new IllegalArgumentException("apkFile null");
        }

        if (instance == null) {
            instance = new CertificateProcessor();
        }
        instance.data = data;
        instance.apkFile = apkFile;
        instance.certDatas = new ArrayList<CertificateData>();
        instance.apkNameMarker = apkFile.getMarker();
        return instance;
    }

    public static CertificateProcessor getInstance(ApkFile apkFile) {
        if (instance == null) {
            instance = new CertificateProcessor();
        }
        if (apkFile == null) {
            throw new IllegalArgumentException("apkFile null");
        }
        instance.data = null;
        instance.certDatas = new ArrayList<CertificateData>();
        instance.apkFile = apkFile;
        instance.apkNameMarker = apkFile.getMarker();
        return instance;
    }

    public List<CertificateData> processCertificates() {
        return processCertificates(new File(apkFile.getUnzipDirectoryWithUnzipedData(), "META-INF"));
    }

    public List<CertificateData> processCertificates(File dirWithCertificates) {

        certDatas = new ArrayList<CertificateData>();
        List<File> certs = null;

        try {
            FileFinder ff = new FileFinder(dirWithCertificates);
            certs = ff.getCertificateFilesInDirectories();
        } catch (IllegalArgumentException e) {
            logger.warn(apkNameMarker + "META-INF directory doesn`t exists");
            return null;
        }

        for (File f : certs) {
            processCertificate(f);
        }
        if (data != null) {
            data.setCertificateDatas(certDatas);
        }
        return certDatas;
    }

    private void processCertificate(File file) {

        logger.trace(apkNameMarker + "Started processing of certificate");

        InputStream is = null;
        try {
            is = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            logger.error(apkNameMarker + "Unable to read certificate " + file.getName());
        }

        try {
            PKCS7 pkcs7 = new PKCS7(is);
            X509Certificate[] certificates = pkcs7.getCertificates();

            for (int i = 0; i < certificates.length; ++i) {
                X509Certificate certificate = certificates[i];
                CertificateData data = new CertificateData();

                data.setFileName(file.getName());

                //certificate hashes
                byte[] bytes = certificate.getEncoded();
                String certMd5 = this.md5Digest(bytes);
                data.setCertMd5(certMd5);
                String certBase64Md5 = this.md5Digest(this.byteToHexString(bytes));
                data.setCertBase64Md5(certBase64Md5);

                //public key hash
                PublicKey publicKey = certificate.getPublicKey();
                String publicKeyMd5 = this.md5Digest(this.byteToHexString(publicKey.getEncoded()));
                data.setPublicKeyMd5(publicKeyMd5);

                data.setStartDate(certificate.getNotBefore());
                data.setEndDate(certificate.getNotAfter());
                data.setSignAlgorithm(certificate.getSigAlgName());
                data.setSignAlgorithmOID(certificate.getSigAlgOID());
                data.setIssuerName(certificate.getIssuerX500Principal().getName());
                data.setVersion(certificate.getVersion());

                certDatas.add(data);
            }


        } catch (Exception e) {
            logger.error(e.toString());
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }
        logger.trace(apkNameMarker + "Finished processing of certificate");
    }

    private String md5Digest(byte[] input) throws IOException {
        MessageDigest digest = this.getDigest("Md5");
        digest.update(input);
        return this.getHexString(digest.digest());
    }

    private String md5Digest(String input) throws IOException {
        MessageDigest digest = this.getDigest("Md5");
        digest.update(input.getBytes(StandardCharsets.UTF_8));
        return this.getHexString(digest.digest());
    }

    private String byteToHexString(byte[] bArray) {
        StringBuilder sb = new StringBuilder(bArray.length);
        byte[] var4 = bArray;
        int var5 = bArray.length;

        for (int var6 = 0; var6 < var5; ++var6) {
            byte aBArray = var4[var6];
            String sTemp = Integer.toHexString(255 & (char) aBArray);
            if (sTemp.length() < 2) {
                sb.append(0);
            }

            sb.append(sTemp.toUpperCase());
        }

        return sb.toString();
    }

    private String getHexString(byte[] digest) {
        BigInteger bi = new BigInteger(1, digest);
        return String.format("%032x", new Object[]{bi});
    }

    private MessageDigest getDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException var3) {
            throw new RuntimeException(var3.getMessage());
        }
    }

}