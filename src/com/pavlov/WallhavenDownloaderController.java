package com.pavlov;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.text.Text;
import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WallhavenDownloaderController {
    private static final String baseURL = "https://alpha.wallhaven.cc/search?";
    private static final File error = new File("error.txt");
    private static String outputDir = System.getenv("USERPROFILE") + "\\Documents\\WallhavenDownloader";

    @FXML
    private CheckBox categoryGeneral;

    @FXML
    private CheckBox categoryAnime;

    @FXML
    private CheckBox categoryPeople;

    @FXML
    private ChoiceBox<String> resolutionBox;

    @FXML
    private ChoiceBox<String> purityBox;

    @FXML
    private TextField wallpaperCount;

    @FXML
    private TextField searchQuery;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private Text log;

    @FXML
    public void initialize() {
        final String[] resolutionListArr = {"Random", "1024x768", "1280x800", "1366x768", "1280x960", "1440x900", "1600x900", "1280x1024", "1600x1200", "1680x1050", "1920x1080", "1920x1200", "2560x1440", "2560x1600", "3840x1080", "5760x1080", "3840x2160", "5120x2880"};
        resolutionBox.setItems(FXCollections.observableArrayList(Arrays.asList(resolutionListArr)));
        resolutionBox.setValue("Random");
        final String[] purityListArr = {"Both", "SFW", "Sketchy"};
        purityBox.setItems(FXCollections.observableArrayList(Arrays.asList(purityListArr)));
        purityBox.setValue("Both");

        if (!Files.exists(Paths.get(outputDir))) {
            try {
                Files.createDirectory(Paths.get(outputDir));
            } catch (IOException e) {
                this.writeToErrorLog(e.getLocalizedMessage());
            }
        }
    }

    @FXML
    private void startDownloadOnClick() {
        Runnable task = () -> {
            this.progressBar.setProgress(0);
            int count = Integer.valueOf(this.wallpaperCount.getText());
            this.updateLogLabel("Getting " + count + " ids");
            ArrayList<String> ids = getWallpaperIds(count);
            String[] exts = {".jpg", ".png", ".bmp"};
            for (String id : ids) {
                this.updateLogLabel("Downloading wallpaper "  + id);
                for (String extension : exts) {
                    try {
                        URL imageURL = new URL("https://wallpapers.wallhaven.cc/wallpapers/full/wallhaven-" + id + extension);
                        URLConnection conn = imageURL.openConnection();
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:47.0) Gecko/20100101 Firefox/47.0");
                        conn.connect();
                        FileUtils.copyInputStreamToFile(conn.getInputStream(), Paths.get(outputDir + File.separator + String.valueOf(id) + extension).toFile());
                        updateProgressBar(1.0 / count);
                        this.updateLogLabel("Downloaded wallpaper "  + id);
                    } catch (Exception ignored) {
                    }
                }
            }

            this.updateLogLabel("Done");
        };
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private ArrayList<String> getWallpaperIds(int count) {
        int pageNumber = 1;
        ArrayList<String> ids = new ArrayList<>();
        boolean found;

        while (true) {
            found = false;
            String url = this.getURL() + pageNumber;
            Document page = null;
            try {
                page = Jsoup.connect(url).userAgent("Mozilla/5.0 (X11; Linux x86_64; rv:47.0) Gecko/20100101 Firefox/47.0").timeout(0).get();
            } catch (IOException e) {
                this.writeToErrorLog("Cannot connect to " + e.getMessage());
                System.exit(1);
            }

            Elements links = page.select("a[href]");
            StringBuilder linksString = new StringBuilder();
            for (Element wallpaperLink : links) {
                linksString.append(wallpaperLink.attr("abs:href")).append(" ");
            }
            String patternString = "https://alpha\\.wallhaven\\.cc/wallpaper/(\\d+)\\s";
            Pattern pattern = Pattern.compile(patternString);
            Matcher matcher = pattern.matcher(linksString);

            while (matcher.find()) {
                ids.add(matcher.group(1));
                found = true;
                if (ids.size() >= count) {
                    return ids;
                }
            }

            if (!found){
                this.updateLogLabel("Done.");
                Platform.runLater(() -> progressBar.setProgress(0));
                return ids;
            }
            ++pageNumber;
        }
    }

    @FXML
    private void openDirectoryOnClick() {
        try {
            Desktop.getDesktop().open(new File(System.getenv("USERPROFILE") + "\\Documents\\WallhavenDownloader\\"));
        } catch (IOException e) {
            this.writeToErrorLog("Cannot open " + System.getenv("USERPROFILE") + "\\Documents\\WallhavenDownloader\\" + e.getMessage());
        }
    }

    private void updateProgressBar(double progress) {
        Platform.runLater(() -> progressBar.setProgress(progressBar.getProgress() + progress));
    }

    private void updateLogLabel(String message){
        Platform.runLater(() -> log.setText(message));
    }

    private String getURL() {
        StringBuilder sb = new StringBuilder(baseURL);
        String keywords = getQuery();
        if (!keywords.equals("")) {
            sb.append("q=").append(keywords);
        }
        sb.append("&categories=").append(getCategories());
        sb.append("&purity=").append(getPurity());
        String resolution = getResolution();
        if (!resolution.equals("")) {
            sb.append("&resolutions=").append(resolution);
        }
        sb.append("&sorting=date_added&order=desc&page=");
        return sb.toString();
    }

    private String getCategories() {
        String categories = "";
        categories += this.categoryGeneral.isSelected() ? '1' : '0';
        categories += this.categoryAnime.isSelected() ? '1' : '0';
        categories += this.categoryPeople.isSelected() ? '1' : '0';

        if (categories.equals("000")) {
            return "111";
        } else {
            return categories;
        }
    }

    private String getPurity() {
        switch (purityBox.getValue()) {
            case "SFW":
                return "100";
            case "Sketchy":
                return "010";
            case "Both":
                return "110";
            default:
                throw new IllegalArgumentException("Invalid purity parameter");
        }
    }

    private String getResolution() {
        if (resolutionBox.getValue().equals("Random")) {
            return "";
        } else {
            return resolutionBox.getValue();
        }
    }

    private String getQuery() {
        String[] keywords = this.searchQuery.getText().split("\\s+");
        StringBuilder sb = new StringBuilder("");
        for (int i = 0; i < keywords.length - 1; i++) {
            sb.append(keywords[i]).append('+');
        }
        sb.append(keywords[keywords.length - 1]);

        return sb.toString();
    }

    private void writeToErrorLog(String message) {
        if (!error.exists()) {
            try {
                error.createNewFile();
            } catch (IOException e) {
                this.updateLogLabel("Cannot create error.txt");
            }
        }
        try (FileWriter fw = new FileWriter(error, true)) {
            Date now = new Date();
            fw.write(now + " " + message + System.lineSeparator());
        } catch (IOException ignored) {
            this.updateLogLabel("Cannot write to error.txt");
        }
    }
}
