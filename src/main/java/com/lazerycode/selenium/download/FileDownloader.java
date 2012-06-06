package com.lazerycode.selenium.download;

import com.lazerycode.selenium.SeleniumServerMojo;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.*;
import java.net.URL;
import java.util.Enumeration;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.apache.commons.io.FileUtils.copyURLToFile;
import static org.codehaus.plexus.util.FileUtils.cleanDirectory;
import static org.codehaus.plexus.util.FileUtils.deleteDirectory;

public class FileDownloader extends SeleniumServerMojo {

    private URL remoteFile;
    private String filename;
    private boolean performHashCheck = true;
    private String SHA1Hash;
    private String downloadPath = System.getProperty("java.io.tmpdir") + "/" + UUID.randomUUID().toString().replaceAll("-", "") + "/";
    private String localFilePath;
    private int timeout = 30000;
    private int defaultNumberOfRetryAttempts = 1;
    private int totalNumberOfRetryAttempts = 1;

    public FileDownloader() {
    }

    /**
     * Get the current location that files will be downloaded to.
     *
     * @return The filepath that the file will be downloaded to.
     */
    public String localFilePath() {
        return this.localFilePath;
    }

    /**
     * Set the SHA1 hash that will be used to check that the file is valid
     *
     * @param value The SHA1 Hash
     */
    public void sha1Hash(String value) {
        this.SHA1Hash = value;
    }

    /**
     * Perform a SHA1 Hash check on downloaded files to ensure they are not corrupted.
     *
     * @param value
     */
    public void performSHA1HashCheck(boolean value) {
        this.performHashCheck = value;
    }

    public void specifyTotalNumberOfRetryAttempts(int value) {
        if (value < 0) {
            getLog().warn("Invalid number of retry attempts specified, defaulting to '" + this.defaultNumberOfRetryAttempts + "'...");
            this.totalNumberOfRetryAttempts = this.defaultNumberOfRetryAttempts;
        } else {
            this.totalNumberOfRetryAttempts = value;
        }
    }

    /**
     * Set the path that files will be downloaded to.
     *
     * @param value The filepath that the file will be downloaded to.
     */
    public void localFilePath(String value) {
        this.localFilePath = value;
    }

    public void remoteURL(URL value) throws MojoExecutionException {
        this.remoteFile = value;
        this.filename = value.getFile();
    }

    public URL remoteURL() {
        return this.remoteFile;
    }

    public String localfilename() {
        return this.filename;
    }

    public void downloadZipAndExtractFiles() throws Exception {
        File zipToDownload = downloadFile();
        //Extract files from zip file and copy them to correct location
        ZipFile zip = new ZipFile(zipToDownload);
        Enumeration<ZipEntry> entries = (Enumeration<ZipEntry>) zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry zipFileEntry = entries.nextElement();
            File extractedFile = new File(this.localFilePath, zipFileEntry.getName());
            if (zipFileEntry.isDirectory()) {
                continue;
            } else if (!extractedFile.exists()) {
                extractedFile.getParentFile().mkdirs();
                extractedFile.createNewFile();
            }
            InputStream is = zip.getInputStream(zipFileEntry);
            OutputStream os = new FileOutputStream(extractedFile);
            while (is.available() > 0) {
                os.write(is.read());
            }
            os.close();
            is.close();
        }
        zip.close();
        cleanDirectory(this.downloadPath);
        deleteDirectory(this.downloadPath);
        getLog().info("File copied to " + this.localFilePath);
    }

    private File downloadFile() throws IOException, MojoExecutionException {
        File zipToDownload = new File(this.downloadPath + File.separator + this.filename);

        if (!fileExistsAndIsValid(zipToDownload)) {
            getLog().info("Downloading '" + zipToDownload.getName() + "'...");
            for (int n = 0; n < this.totalNumberOfRetryAttempts; n++) {
                try {
                    copyURLToFile(this.remoteFile, zipToDownload, this.timeout, this.timeout);
                    if (fileExistsAndIsValid(zipToDownload)) break;
                } catch (IOException ex) {
                    getLog().info("Problem downloading '" + zipToDownload.getName() + "'... " + ex.getLocalizedMessage());
                }
            }
        } else {
            getLog().info("A valid copy of '" + zipToDownload.getName() + "' already exists.");
        }

        if (!fileExistsAndIsValid(zipToDownload)) throw new MojoExecutionException("Unable to successfully downloaded '" + zipToDownload.getName() + "'!");

        return zipToDownload;
    }

    private boolean fileExistsAndIsValid(File fileToCheck) throws IOException {
        if (fileToCheck.exists()) {
            if (performHashCheck) return hashCheckSHA1(fileToCheck);
            return true;
        }
        return false;
    }

    private boolean hashCheckSHA1(File zipToDownload) throws IOException {
        if (this.SHA1Hash.equals(DigestUtils.shaHex(new FileInputStream(zipToDownload)))) return true;
        return false;
    }
}