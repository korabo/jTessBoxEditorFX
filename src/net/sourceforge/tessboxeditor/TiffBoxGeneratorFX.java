/**
 * Copyright @ 2016 Quan Nguyen
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package net.sourceforge.tessboxeditor;

import java.awt.Graphics2D;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import net.sourceforge.tessboxeditor.datamodel.TessBox;
import net.sourceforge.tessboxeditor.datamodel.TessBoxCollection;
import net.sourceforge.tessboxeditor.utilities.ImageUtils;
import net.sourceforge.tess4j.util.ImageIOHelper;
import static net.sourceforge.tessboxeditor.utilities.Utils.deriveFont;
import org.imgscalr.Scalr;

public class TiffBoxGeneratorFX {

    static final String EOL = System.getProperty("line.separator");
    private final List<List<String>> textPages;
    private final List<BufferedImage> imagePages = new ArrayList<>();
    private final List<TessBoxCollection> boxPages = new ArrayList<>();
    private final Font font;
    private int width, height;
    private int noiseAmount;
    private int margin = 100;
    private String fileName = "fontname.exp0";
    private File outputFolder;
    private final int COLOR_WHITE = java.awt.Color.WHITE.getRGB();
    private final int COLOR_BLACK = java.awt.Color.BLACK.getRGB();
    private float tracking = TextAttribute.TRACKING_LOOSE; // 0.04
    private int leading = 12;
    private boolean isAntiAliased;
//    private final File baseDir = Utils.getBaseDir(TiffBoxGeneratorFX.this);
    private final TextFlow textFlow;

    private static final double IMG_SCALE_ = 1.0D;
    public static final double TIF_RESO_ = 300;
    public static final double FONT_SCALE_ = 4.0D;
    public static final double SCRN_RESO_1 = 72;
    public static final double SCRN_RESO_2 = 96;
    private static double IMG_SCALE = IMG_SCALE_;
    private static double FONT_SCALE = FONT_SCALE_;
    public static double CHR_SPC_R = 0.1d;

    public static void setImageScale(double scale) {
        if (scale <= 0) {
            // reset
            TiffBoxGeneratorFX.IMG_SCALE = TiffBoxGeneratorFX.IMG_SCALE_;
        } else {
            TiffBoxGeneratorFX.IMG_SCALE = scale;
        }
    }

    public static void setTiffDensity(double reso) {
        if (reso <= 0) {
            // reset
            ImageIOHelper.IMG_DPI_X = (int) TiffBoxGeneratorFX.TIF_RESO_;
            ImageIOHelper.IMG_DPI_Y = (int) TiffBoxGeneratorFX.TIF_RESO_;
        } else {
            ImageIOHelper.IMG_DPI_X = (int) reso;
            ImageIOHelper.IMG_DPI_Y = (int) reso;
        }
    }

    public static void setFontScale(double ftsc) {
        if (ftsc <= 0) {
            // reset
            TiffBoxGeneratorFX.FONT_SCALE = TiffBoxGeneratorFX.FONT_SCALE_;
        } else {
            TiffBoxGeneratorFX.FONT_SCALE = ftsc;
        }
    }
    
    static BufferedImage resizeImage(BufferedImage originalImage, int newWidth, int newHeight) {
        BufferedImage image =
                Scalr.resize(originalImage, Scalr.Method.BALANCED, newWidth, newHeight);
        return image;
    }
    
    private Bounds resizeBounds(Bounds bounds, double scaleX, double scaleY) {
        final double dltW = font.getSize() * scaleX * CHR_SPC_R -0.5d;
        final double dltH = font.getSize() * scaleX * CHR_SPC_R - 0.5d;
        Bounds newBs = new BoundingBox(
                bounds.getMinX() * scaleX -dltW, bounds.getMinY()* scaleY -dltH, bounds.getMinZ(), 
                bounds.getWidth() * scaleX +(dltW*2.0d), bounds.getHeight() * scaleY +(dltH*2.0d), bounds.getDepth());
        return newBs;
    }

    private final static Logger logger = Logger.getLogger(TiffBoxGeneratorFX.class.getName());

    public TiffBoxGeneratorFX(List<List<String>> textPages, Font font, int width, int height) {
        this.textPages = textPages;
        this.font = deriveFont(font, font.getSize() * TiffBoxGeneratorFX.FONT_SCALE); // adjustment
        double scale = 1.0d;
        if (TiffBoxGeneratorFX.FONT_SCALE_ == TiffBoxGeneratorFX.FONT_SCALE ) {
            // none
        } else {
            scale = TiffBoxGeneratorFX.FONT_SCALE / TiffBoxGeneratorFX.FONT_SCALE_;
        }
        this.width = (int) Math.ceil((double) width * scale);
        this.height = (int) Math.ceil((double) height * scale);
        textFlow = new TextFlow();
        textFlow.setPrefWidth(this.width);
        textFlow.setPadding(new Insets((int) Math.floor((double) margin * scale)));
        textFlow.setStyle("-fx-letter-spacing: " + tracking); // no effect; not supported yet in JDK8u101
        textFlow.setLineSpacing((int) Math.ceil((double) leading * scale) + 4); // adjustment
    }

    public void create() {
        this.layoutPages();
        this.saveMultipageTiff();
        this.saveBoxFile();
    }

    String createFileName(Font font) {
        return font.getFamily().replace(" ", "").toLowerCase() + (font.getStyle().contains("Bold") ? "b" : "") + (font.getStyle().contains("Italic") ? "i" : "");
    }

    /**
     * Formats box content.
     *
     * @return
     */
    private String formatOutputString() {
        StringBuilder sb = new StringBuilder();
//        String combiningSymbols = readCombiningSymbols();
        for (short pageIndex = 0; pageIndex < imagePages.size(); pageIndex++) {
            TessBoxCollection boxCol = boxPages.get(pageIndex);
            int pageH = boxCol.pageHeight;
//            boxCol.setCombiningSymbols(combiningSymbols);
//            boxCol.combineBoxes();

            for (TessBox box : boxCol.toList()) {
                Rectangle2D rect = box.getRect();
                sb.append(String.format("%s %.0f %.0f %.0f %.0f %d", box.getCharacter(),
                        Math.floor(rect.getMinX()), pageH - Math.ceil(rect.getMinY() + rect.getHeight()),
                        Math.ceil(rect.getMinX() + rect.getWidth()), pageH - Math.floor(rect.getMinY()),
                        pageIndex)).append(EOL);
            }
        }
//        if (isTess2_0Format) {
//            return sb.toString().replace(" 0" + EOL, EOL); // strip the ending zeroes
//        }
        return sb.toString();
    }

    /**
     * Gets bounding box of a Text node.
     *
     * @param text
     * @return bounding box
     */
    Bounds getBoundingBox(Text text) {
        Bounds tb = text.getBoundsInParent();
        Rectangle stencil = new Rectangle(tb.getMinX()-1.0d, tb.getMinY()-1.0d, tb.getWidth()+2.0d, tb.getHeight()+2.0d);
        Shape intersection = Shape.intersect(text, stencil);
        Bounds ib = intersection.getBoundsInParent();
        return ib;
    }

    /**
     * Tightens bounding box in four directions b/c Java cannot produce bounding
     * boxes as tight as Tesseract can. Exam only the first pixel on each side.
     *
     * @param rect
     * @param bi
     */
    private Bounds tightenBoundingBox(Bounds rectShape, BufferedImage bi) {
        java.awt.Rectangle rect = new java.awt.Rectangle(
                (int) Math.floor(rectShape.getMinX()) - 0, (int) Math.floor(rectShape.getMinY()) - 1,
                (int) Math.ceil(rectShape.getWidth()) + 0, (int) Math.ceil(rectShape.getHeight()) + 1
        );

        // left
        int startX = rect.x - 1;
        int endX = rect.x + 2;
        int rcW = rect.width + 1;
        Integer colorInt;
        outerLeft:
        for (int x = startX; x <= endX; x++, rcW--) {
            rect.x = x;
            rect.width = rcW;
            for (int y = rect.y; y < rect.y + rect.height; y++) {
                if ( (colorInt = getRGB(bi, x, y)) == null ) {
                    break;
                }
                if (colorInt == COLOR_BLACK) {
                    break outerLeft;
                }
            }
        }

        // right
        startX = rect.x + rect.width + 1;
        endX = rect.x + rect.width - 4;
        rcW = startX - rect.x;
        outerRight:
        for (int x = startX; x >= endX; x--, rcW--) {
            rect.width = rcW;
            for (int y = rect.y; y < rect.y + rect.height; y++) {
                if ( (colorInt = getRGB(bi, x, y)) == null ) {
                    break;
                }
                if (colorInt == COLOR_BLACK) {
                        break outerRight;
                }
            }
        }
        //TODO: Need to account for Java's incorrect over-tightening the top of the bounding box
        // Need to move the top up by 1px and increase the height by 1px
        // top
        int startY = rect.y - 2;
        int endY = rect.y + 4;
        int rcH = rect.height + 2;
        outerTop:
        for (int y = startY; y <= endY; y++, rcH--) {
            rect.y = y;
            rect.height = rcH;
            for (int x = rect.x; x < rect.x + rect.width; x++) {
                if ( (colorInt = getRGB(bi, x, y)) == null ) {
                    break;
                }
                if (colorInt == COLOR_BLACK) {
                    break outerTop;
                }
            }
        }

        // bottom
        startY = rect.y + rect.height + 1;
        endY = rect.y + rect.height - 4;
        rcH = startY - rect.y;
        outerBottom:
        for (int y = startY; y >= endY; y--, rcH--) {
            rect.height = rcH;
            for (int x = rect.x; x < rect.x + rect.width; x++) {
                if ( (colorInt = getRGB(bi, x, y)) == null ) {
                    break;
                }
                if (colorInt == COLOR_BLACK) {
                    break outerBottom;
                }
            }
        }
        return new BoundingBox(rect.x, rect.y, rect.width, rect.height);
    }
    
    private Integer getRGB(BufferedImage bi, int x, int y) {
        try {
            return bi.getRGB(x, y);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Creates box file.
     */
    private void saveBoxFile() {
        try {
            try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(outputFolder, fileName + ".box")), StandardCharsets.UTF_8))) {
                out.write(formatOutputString());
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    /**
     * Add Text nodes to TextFlow, which is one per page.
     */
    private void layoutPages() {
        boxPages.clear();
        imagePages.clear();

//        Scene scene = new Scene(textFlow, width, height);

        for (List<String> textPage : textPages) {
            // for add CHARSPACING 180621 ST(KRB)
//            List<Text> texts = new ArrayList<Text>();
            List<Node> nodes = new ArrayList<>();
            textFlow.getChildren().clear();
            final double fsize = font.getSize();
            for (String ch : textPage) {
                // each ch can have multiple Unicode codepoints
                Text text = new Text(ch);
                text.setFont(font);
                // for add CHARSPACING 180621 ST(KRB)
                nodes.add(text);
                    Rectangle box = new Rectangle(fsize * CHR_SPC_R, fsize, Color.TRANSPARENT);
                    nodes.add(box);
            }

            textFlow.getChildren().addAll(nodes);

//            StackPane pane = new StackPane();
//            pane.setAlignment(Pos.TOP_LEFT);
//            pane.getChildren().addAll(textFlow);
            // No need to show
//        Stage stage = new Stage();
//        stage.setTitle("TIFF/Boxes");
//        stage.setScene(scene);
//        stage.show();
            drawPage();
        }
    }

    /**
     * Takes snapshot of each text flow and store as <code>BufferedImage</code>.
     */
    private void drawPage() {
        BufferedImage bi;// = new BufferedImage(width, height, isAntiAliased ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_BYTE_BINARY);
        final SnapshotParameters snapshotParameters = new SnapshotParameters();
        snapshotParameters.setFill(Color.WHITE);

        WritableImage snapshot = textFlow.snapshot(snapshotParameters, null);
        bi = SwingFXUtils.fromFXImage(snapshot, null);
        // image scaling according to designated image_scale
        double scale = TiffBoxGeneratorFX.IMG_SCALE_;
        if (scale != TiffBoxGeneratorFX.IMG_SCALE) {
            scale = TiffBoxGeneratorFX.IMG_SCALE;
            int w = (int) (bi.getWidth() * scale);
            int h = (int) (bi.getHeight() * scale);
            bi = TiffBoxGeneratorFX.resizeImage(bi, w, h);
        }

        width = bi.getWidth();
        height = bi.getHeight();
        bi = redraw(bi);
        imagePages.add(bi);

        TessBoxCollection boxCol = new TessBoxCollection(); // for each page
        boxCol.pageWidth = bi.getWidth();
        boxCol.pageHeight = bi.getHeight();
        boxPages.add(boxCol);
        short pageNum = 0;
        List<Node> nodes = textFlow.getChildren();
        for (int i = 0; i < nodes.size(); i++) {
            if (!(nodes.get(i) instanceof Text)) 
                continue;
            Text text = (Text) nodes.get(i);
            String ch = text.getText();
            if (ch.length() == 0 || Character.isWhitespace(ch.charAt(0))) {
                // skip spaces
                continue;
            }

            // get bounding box for each character
            Bounds bounds = getBoundingBox(text);
            bounds = resizeBounds(bounds, scale, scale);
            bounds = tightenBoundingBox(bounds, bi);
//            System.out.println(bounds);
            if (bounds.getWidth() <= 0 || bounds.getHeight() <= 0) {
                // skip bad boxes
                logger.log(Level.WARNING, "ILL-Bouds:{0} of text:{1}", new Object[]{bounds, text});
                continue;
            }

            boxCol.add(new TessBox(ch, new Rectangle2D(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight()), pageNum));
        }
    }

    /**
     * Reduces bit depth of 32bpp snapshot from to 8bpp or 1bpp depending on
     * anti-aliased mode selection.
     *
     * @param bi
     * @return
     */
    BufferedImage redraw(BufferedImage bi) {
        BufferedImage newImage = new BufferedImage(bi.getWidth(), bi.getHeight(), isAntiAliased ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g2d = newImage.createGraphics();
        g2d.drawImage(bi, 0, 0, null);
        g2d.dispose();
        return newImage;
    }

    /**
     * Creates a multi-page TIFF image.
     */
    private void saveMultipageTiff() {
        try {
            File tiffFile = new File(outputFolder, fileName + ".tif");
            tiffFile.delete();
            BufferedImage[] images = imagePages.toArray(new BufferedImage[imagePages.size()]);
            if (noiseAmount != 0) {
                for (int i = 0; i < images.length; i++) {
                    images[i] = ImageUtils.addNoise(images[i], noiseAmount);
                }
            }
            ImageIOHelper.mergeTiff(images, tiffFile, (isAntiAliased || noiseAmount != 0) ? "LZW" : "CCITT T.6");  // CCITT T.6 for bitonal; LZW for others);
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    /**
     * Sets output filename.
     *
     * @param fileName the fileName to set
     */
    public void setFileName(String fileName) {
        if (fileName != null && fileName.length() > 0) {
            int index = fileName.lastIndexOf(".");
            this.fileName = index > -1 ? fileName.substring(0, index) : fileName;
        }
    }

    /**
     * Sets letter tracking (letter spacing).
     *
     * @param tracking the tracking to set
     */
    public void setTracking(float tracking) {
        this.tracking = tracking;
        textFlow.setStyle("-fx-letter-spacing: " + tracking); // no effect; not supported yet in JDK8u91
    }

    /**
     * Sets leading (line spacing).
     *
     * @param leading the leading to set
     */
    public void setLeading(int leading) {
        this.leading = leading;
        textFlow.setLineSpacing(leading + 4); // adjustment
    }

    /**
     * Sets output folder.
     *
     * @param outputFolder the outputFolder to set
     */
    public void setOutputFolder(File outputFolder) {
        this.outputFolder = outputFolder;
    }

    /**
     * Enables text anti-aliasing.
     *
     * @param enabled on or off
     */
    public void setAntiAliasing(boolean enabled) {
        this.isAntiAliased = enabled;
    }

    /**
     * Sets amount of noise to be injected to the generated image.
     *
     * @param noiseAmount the noiseAmount to set
     */
    public void setNoiseAmount(int noiseAmount) {
        this.noiseAmount = noiseAmount;
    }

    /**
     * Sets margin of text within image.
     *
     * @param margin the margin to set
     */
    public void setMargin(int margin) {
        this.margin = margin;
        textFlow.setPadding(new Insets(margin));
    }
}
