import org.apache.commons.io.FileUtils;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Raspberry2FTP {

    private static int port = 21;
    //should be put target FTP IP
    private static String server = "";
    //should be put user/pass
    private static String user = "";
    private static String pass = "";


    private static FTPClient ftpClient;
    private static String path = "/home/pi/Desktop/screen/";
    private static FileInputStream fis = null;
    private static List<String> fileList = new ArrayList<>();

    private static Logger log = Logger.getLogger("Raspberry2FTP");

    public static void main(String[] args) {
        createFTPConnection();
        saveFiles();
    }

    private static void saveFiles() {
        try {
            while (true) {
                fis = null;
                File folder = new File(path);
                File[] listOfFiles = folder.listFiles();
                for (int i = 0; i < listOfFiles.length; i++) {
                    File file = listOfFiles[i];

                    if (file.isFile() && file.getName().endsWith(".jpg")) {
                        //copy file to FTP
                        if (!fileList.contains(file.getName())) {
                            long fileSizeInit = file.length();
                            long fileSizeFinal = getFileSize(file);
                            if (fileSizeInit == fileSizeFinal) {
                                fis = new FileInputStream(file);
                                ftpClient.storeFile(file.getName(), fis);
                                fis.close();
                                fileList.add(file.getName());
                            }
                        }

                        //move file if it is older than 1 day
                        DateTime createDate = new DateTime(file.lastModified());
                        if (getDateDifference(createDate) > 1) {
                            moveFile(createDate, file, folder);
                            fileList.remove(file.getName());
                        }
                    }
                    //delete directory if it is older than 3 days
                    else if (getFolderCreatedDate(file) > 3) {
                        FileUtils.forceDelete(file);
                        log.info("Raspberry2FTP: Deleted archive: " + file.getName());
                    }
                }
            }

        } catch (IOException e) {
            String errorMessage = "Raspberry2FTP: screen transfer app failed";
            log.error(errorMessage, e);
            e.printStackTrace();
            Phone.sendMessage(errorMessage);
        }
    }

    private static int getFolderCreatedDate(File file) throws IOException {
        DateTime today = new DateTime();
        DateTimeFormatter dtf = DateTimeFormat.forPattern("dd-MM-yyyy");
        DateTime createdDate = dtf.parseDateTime(file.getName());
        int days = Days.daysBetween(createdDate, today).getDays();
        return days;
    }

    private static int getDateDifference(DateTime createDate) {
        DateTime today = new DateTime();
        int days = Days.daysBetween(createDate, today).getDays();
        return days;
    }

    private static void moveFile(DateTime createDate, File file, File folder) {
        File theDir = new File(folder + File.separator + createDate.dayOfMonth().getAsString() + "-" + createDate.monthOfYear().getAsString() + "-" + createDate.year().getAsString());
        if (!theDir.exists()) {
            theDir.mkdir();
        }
        file.renameTo(new File(theDir.getPath() + File.separator + file.getName()));
    }

    private static long getFileSize(File file) {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String errorMessage = "Raspberry2FTP: get screen size error";
            log.error(errorMessage, e);
            e.printStackTrace();
            Phone.sendMessage(errorMessage);
        }
        return file.length();
    }

    private static void createFTPConnection() {
        ftpClient = new FTPClient();
        try {
            ftpClient.connect(server, port);
            String reply = showServerReply(ftpClient);

            int replyCode = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                String errorMessage = "Raspberry2FTP: FTP connection failed";
                log.error(errorMessage + ": " + reply);
                Phone.sendMessage(errorMessage);
                return;
            }

            boolean success = ftpClient.login(user, pass);
            reply = showServerReply(ftpClient);

            if (!success) {
                String errorMessage = "Raspberry2FTP: could not login to FTP";
                log.error(errorMessage + ": " + reply);
                Phone.sendMessage(errorMessage);
                return;
            }
        } catch (IOException e) {
            String errorMessage = "Raspberry2FTP: connect to FTP error";
            log.error(errorMessage, e);
            Phone.sendMessage(errorMessage);
            e.printStackTrace();
            try {
                ftpClient.logout();
                ftpClient.disconnect();
            } catch (IOException ex) {
                String exMessage = "Raspberry2FTP: disconnect on FTP error";
                log.error(exMessage, ex);
                ex.printStackTrace();
                Phone.sendMessage(exMessage);
            }
        }
    }

    private static String showServerReply(FTPClient ftpClient) {
        String reply = null;
        String[] replies = ftpClient.getReplyStrings();
        if (replies != null && replies.length > 0) {
            for (String aReply : replies) {
                reply = aReply;
                log.info("Raspberry2FTP: SERVER: " + aReply);
            }
        }
        return reply;
    }
}
