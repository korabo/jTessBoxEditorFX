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

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextField;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tessboxeditor.datamodel.TessBox;
import net.sourceforge.tessboxeditor.datamodel.TessBoxCollection;

public class BoxEditorEditController extends BoxEditorController {

    @FXML
    private Button btnMerge;
    @FXML
    private Button btnSplit;
    @FXML
    private Button btnInsert;
    @FXML
    private Button btnDelete;
    @FXML
    private Button btnMarkEOL;

    private OcrSegmentWorker ocrSegmentWorker;

    /**
     * Event handler.
     *
     * @param event
     */
    @FXML
    @Override
    protected void handleAction(ActionEvent event) {
        if (event.getSource() == btnMerge) {
            mergeAction(event);
        } else if (event.getSource() == btnSplit) {
            splitAction(event);
        } else if (event.getSource() == btnInsert) {
            insertAction(event);
        } else if (event.getSource() == btnDelete) {
            deleteAction(event);
        } else if (event.getSource() == btnMarkEOL) {
            markEOLAction(event);
        } else {
            super.handleAction(event);
        }
    }

    void mergeAction(ActionEvent evt) {
        if (boxes == null) {
            return;
        }
        List<TessBox> selected = boxes.getSelectedBoxes();
//        selected = this.tableView.getSelectionModel().getSelectedItems();
        if (selected.size() <= 1) {
            Alert alert = new Alert(Alert.AlertType.NONE, "Please select more than one box for Merge operation.", ButtonType.OK);
            alert.show();
            return;
        }

        double minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = 0, maxY = 0;

        String chrs = "";
        short page = 0;
        int index = 0;

        for (TessBox box : selected) {
            chrs += box.getCharacter();
            page = box.getPage();
            index = this.boxes.toList().indexOf(box);
            Rectangle2D rect = box.getRect();
            minX = Math.min(minX, rect.getMinX());
            minY = Math.min(minY, rect.getMinY());
            maxX = Math.max(maxX, rect.getMaxX());
            maxY = Math.max(maxY, rect.getMaxY());
            this.boxes.remove(box);
        }

        if (chrs.length() > 0) {
            TessBox newBox = new TessBox(chrs, new Rectangle2D(minX, minY, maxX - minX, maxY - minY), page);
            boxes.add(index, newBox);
            this.tableView.getSelectionModel().clearAndSelect(index);
        }

        this.imageCanvas.paint();
    }

    void splitAction(ActionEvent evt) {
        if (boxes == null) {
            return;
        }
        List<TessBox> selected = boxes.getSelectedBoxes();
        if (selected.size() <= 0) {
            Alert alert = new Alert(Alert.AlertType.NONE, "Please select a box to split.", ButtonType.OK);
            alert.show();
            return;
        } else if (selected.size() > 1) {
            Alert alert = new Alert(Alert.AlertType.NONE, "Please select only one box for Split operation.", ButtonType.OK);
            alert.show();
            return;
        }

        boolean modifierKeyPressed = false;
//        int modifiers = evt.getModifiers();
//        if ((modifiers & ActionEvent.CTRL_MASK) == ActionEvent.CTRL_MASK
//                || (modifiers & ActionEvent.ALT_MASK) == ActionEvent.ALT_MASK
//                || (modifiers & ActionEvent.META_MASK) == ActionEvent.META_MASK) {
//            modifierKeyPressed = true;
//        }

        TessBox box = selected.get(0);
        int index = this.boxes.toList().indexOf(box);
        Rectangle2D rect = box.getRect();
        double w = rect.getWidth();
        double h = rect.getHeight();
        if (!modifierKeyPressed) {
            w /= 2;
            //tableModel.setValueAt(String.valueOf(w), index, 3);
        } else {
            h /= 2;
            //tableModel.setValueAt(String.valueOf(h), index, 4);
        }

        // reduce size of 1st box
        box.setRect(new Rectangle2D(rect.getMinX(), rect.getMinY(), w, h));

        TessBox newBox = new TessBox(box.getCharacter(), new Rectangle2D(rect.getMinX() + w, rect.getMinY(), w, h), box.getPage());
        boxes.add(index + 1, newBox);
//        Rectangle2D newRect = newBox.getRect();
//        double x = newRect.getMinX();
//        double y = newRect.getMinY();
//        if (!modifierKeyPressed) {
//            x += newRect.getWidth();
//        } else {
//            y += newRect.getHeight();
//        }

        tableView.getSelectionModel().clearSelection();
        tableView.getSelectionModel().select(index);
        this.imageCanvas.paint();
    }

    void insertAction(ActionEvent evt) {
        if (boxes == null) {
            return;
        }
        List<TessBox> selected = boxes.getSelectedBoxes();
        if (selected.size() <= 0) {
            Alert alert = new Alert(Alert.AlertType.NONE, "Please select the box to insert after.", ButtonType.OK);
            alert.show();
            return;
        } else if (selected.size() > 1) {
            Alert alert = new Alert(Alert.AlertType.NONE, "Please select only one box for Insert operation.", ButtonType.OK);
            alert.show();
            return;
        }

        TessBox box = selected.get(0);
        int index = this.boxes.toList().indexOf(box);
        index++;
        // offset the new box 15 pixel from the base one
        TessBox newBox = new TessBox(box.getCharacter(), new Rectangle2D(box.getX() + 15, box.getY(), box.getWidth(), box.getHeight()), box.getPage());
        boxes.add(index, newBox);
        tableView.getSelectionModel().clearAndSelect(index);
        this.imageCanvas.paint();
    }

    void deleteAction(ActionEvent evt) {
        if (boxes == null) {
            return;
        }
        List<TessBox> selected = boxes.getSelectedBoxes();
        if (selected.size() <= 0) {
            Alert alert = new Alert(Alert.AlertType.NONE, "Please select a box or more to delete.", ButtonType.OK);
            alert.show();
            return;
        }

        this.tableView.getSelectionModel().clearSelection();
        for (TessBox box : selected) {
            this.boxes.remove(box);
        }

        resetReadout();
        this.imageCanvas.paint();
    }

    void markEOLAction(ActionEvent evt) {
        this.tableView.getScene().setCursor(javafx.scene.Cursor.WAIT);
        this.imageCanvas.setCursor(javafx.scene.Cursor.WAIT);

        // instantiate task for OCR
        ocrSegmentWorker = new OcrSegmentWorker(imageList);
        new Thread(ocrSegmentWorker).start();
    }

    /**
     * A worker class for managing OCR process.
     */
    class OcrSegmentWorker extends Task<Void> {

        List<BufferedImage> imageList;

        public OcrSegmentWorker(List<BufferedImage> imageList) {
            this.imageList = imageList;
        }

        @Override
        protected Void call() throws Exception {
            ITesseract instance = new Tesseract();
            String tessDirectory = ((TextField) btnMarkEOL.getScene().lookup("#tfTessDir")).getText();
            instance.setDatapath(tessDirectory);

            short pageIndex = 0;
            for (BufferedImage image : imageList) {
                // Perform text-line segmentation
                List<Rectangle> regions = instance.getSegmentedRegions(image, ITessAPI.TessPageIteratorLevel.RIL_TEXTLINE);
                TessBoxCollection boxesPerPage = boxPages.get(pageIndex); // boxes per page
                for (Rectangle rect : regions) { // process each line
                    Rectangle2D rect2d = new Rectangle2D(rect.getMinX(), rect.getMinY(), rect.getWidth(), rect.getHeight());
                    TessBox lastBox = boxesPerPage.toList().stream().filter((r) -> {
                        return rect2d.contains(r.getRect());
                    }).reduce((first, second) -> second).orElse(null);

                    if (lastBox == null) {
                        continue;
                    }

                    int index = boxesPerPage.toList().indexOf(lastBox);
                    Rectangle2D rect2 = lastBox.getRect();
                    Rectangle2D nRect = new Rectangle2D(rect2.getMaxX() + 10, rect2.getMinY(), rect2.getWidth(), rect2.getHeight());
                    boxesPerPage.add(index + 1, new TessBox("\t", nRect, pageIndex));
                }
                pageIndex++;
            }

            return null;
        }

        @Override
        protected void succeeded() {
            super.succeeded();
            resetReadout();
            loadTable();
            tableView.getScene().setCursor(javafx.scene.Cursor.DEFAULT);
            imageCanvas.setCursor(javafx.scene.Cursor.DEFAULT);
        }

        @Override
        protected void failed() {
            super.failed();
            tableView.getScene().setCursor(javafx.scene.Cursor.DEFAULT);
            imageCanvas.setCursor(javafx.scene.Cursor.DEFAULT);
        }
    }
}
