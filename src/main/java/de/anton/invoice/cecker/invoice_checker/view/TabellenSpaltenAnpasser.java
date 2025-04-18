package de.anton.invoice.cecker.invoice_checker.view;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Class to manage the widths of columns in a JTable.
 * Original source: https://tips4java.wordpress.com/2008/11/10/table-column-adjuster/
 * Author: Rob Camick
 * Modified slightly for integration.
 *
 * German Class Name: TabellenSpaltenAnpasser
 */
public class TabellenSpaltenAnpasser implements PropertyChangeListener, ComponentListener {
    private JTable table;
    private int spacing;
    private boolean isColumnHeaderIncluded;
    private boolean isColumnDataIncluded;
    private boolean isOnlyAdjustLarger;
    private boolean isDynamicAdjustment;
    private Map<TableColumn, Integer> columnSizes = new HashMap<>();

    /**
     * Specify the table and use default spacing
     * @param table the table to adjust columns for
     */
    public TabellenSpaltenAnpasser(JTable table) {
        this(table, 6);
    }

    /**
     * Specify the table and spacing
     * @param table the table to adjust columns for
     * @param spacing the spacing to add to the calculated width
     */
    public TabellenSpaltenAnpasser(JTable table, int spacing) {
        this.table = table;
        this.spacing = spacing;
        setColumnHeaderIncluded(true);
        setColumnDataIncluded(true);
        setOnlyAdjustLarger(false);
        setDynamicAdjustment(true); // Default to dynamic adjustment
        installActions();
        installListeners(); // Separate method for installing listeners
    }

    /**
     * Adjust the widths of all columns in the table.
     */
    public void adjustColumns() {
        TableColumnModel tcm = table.getColumnModel();
        for (int i = 0; i < tcm.getColumnCount(); i++) {
            adjustColumn(i);
        }
    }

    /**
     * Adjust the width of the specified column in the table.
     * @param column the column index to adjust
     */
    public void adjustColumn(final int column) {
        TableColumn tableColumn = table.getColumnModel().getColumn(column);
        if (!tableColumn.getResizable()) return;

        int columnHeaderWidth = getColumnHeaderWidth(column);
        int columnDataWidth = getColumnDataWidth(column);
        int preferredWidth = Math.max(columnHeaderWidth, columnDataWidth);

        updateTableColumn(column, preferredWidth);
    }

    /**
     * Calculated the width based on the column header
     * @param column the column index
     * @return the width of the column header
     */
    private int getColumnHeaderWidth(int column) {
        if (!isColumnHeaderIncluded) return 0;

        TableColumn tableColumn = table.getColumnModel().getColumn(column);
        Object value = tableColumn.getHeaderValue();
        TableCellRenderer renderer = tableColumn.getHeaderRenderer();
        if (renderer == null) {
            // Use default renderer from JTableHeader if none is explicitly set for the column
            if (table.getTableHeader() != null && table.getTableHeader().getDefaultRenderer() != null) {
                 renderer = table.getTableHeader().getDefaultRenderer();
            } else {
                 // Fallback if even the header's default renderer is null (unlikely but possible)
                 return 0;
            }
        }

        Component c = renderer.getTableCellRendererComponent(table, value, false, false, -1, column);
        return c.getPreferredSize().width;
    }

    /**
     * Calculate the width based on the widest cell renderer for the
     * given column.
     * @param column the column index
     * @return the width of the widest cell in the column
     */
    private int getColumnDataWidth(int column) {
        if (!isColumnDataIncluded) return 0;

        int preferredWidth = 0;
        int maxWidth = table.getColumnModel().getColumn(column).getMaxWidth();

        for (int row = 0; row < table.getRowCount(); row++) {
            preferredWidth = Math.max(preferredWidth, getCellDataWidth(row, column));
            // We've exceeded the maximum width, no need to check other rows
            if (preferredWidth >= maxWidth)
                break;
        }
        return preferredWidth;
    }

    /**
     * Get the preferred width for the specified cell
     * @param row the row index
     * @param column the column index
     * @return the preferred width of the cell
     */
    private int getCellDataWidth(int row, int column) {
        // Invoke the renderer for the cell to calculate the preferred width
        TableCellRenderer cellRenderer = table.getCellRenderer(row, column);
        Component c = table.prepareRenderer(cellRenderer, row, column);
        return c.getPreferredSize().width + table.getIntercellSpacing().width;
    }

    /**
     * Update the TableColumn with the newly calculated width
     * @param column the column index
     * @param width the calculated width
     */
    private void updateTableColumn(int column, int width) {
        final TableColumn tableColumn = table.getColumnModel().getColumn(column);
        if (!tableColumn.getResizable()) return;

        width += spacing;

        // Don't shrink the column width if isOnlyAdjustLarger is true
        if (isOnlyAdjustLarger) {
            width = Math.max(width, tableColumn.getPreferredWidth());
        }

        // Store the original width before setting the new one
        if (!columnSizes.containsKey(tableColumn)) { // Store only if not already stored
            columnSizes.put(tableColumn, tableColumn.getWidth());
        }

        // Need to wrap in SwingUtilities.invokeLater if called from non-EDT thread,
        // but usually called from listeners which are on EDT. Be cautious.
        // Set the width. This might trigger internal table layout updates.
        table.getTableHeader().setResizingColumn(tableColumn); // Helps with visual feedback during resize
        tableColumn.setPreferredWidth(width); // Set preferred width, layout manager should respect this
        // tableColumn.setWidth(width); // Directly setting width can be less reliable with layout managers
    }


    /**
     * Restore the widths of the columns in the table to its previous width.
     */
    public void restoreColumns() {
        TableColumnModel tcm = table.getColumnModel();
        for (int i = 0; i < tcm.getColumnCount(); i++) {
            restoreColumn(i);
        }
    }

    /**
     * Restore the width of the specified column to its previous width.
     * @param column the column index to restore
     */
    private void restoreColumn(int column) {
        TableColumn tableColumn = table.getColumnModel().getColumn(column);
        Integer width = columnSizes.get(tableColumn);
        if (width != null) {
            table.getTableHeader().setResizingColumn(tableColumn);
            tableColumn.setPreferredWidth(width); // Restore preferred width
            // tableColumn.setWidth(width);
        }
    }

    // <editor-fold desc="Getters and Setters for options">
    /**
     * Indicates whether to include the header in the width calculation
     * @param isColumnHeaderIncluded true to include header, false otherwise
     */
    public void setColumnHeaderIncluded(boolean isColumnHeaderIncluded) {
        this.isColumnHeaderIncluded = isColumnHeaderIncluded;
    }

    /**
     * Indicates whether to include the model data in the width calculation
     * @param isColumnDataIncluded true to include model data, false otherwise
     */
    public void setColumnDataIncluded(boolean isColumnDataIncluded) {
        this.isColumnDataIncluded = isColumnDataIncluded;
    }

    /**
     * Indicates whether columns should only be adjusted larger, never smaller.
     * @param isOnlyAdjustLarger true to only adjust larger, false otherwise
     */
    public void setOnlyAdjustLarger(boolean isOnlyAdjustLarger) {
        this.isOnlyAdjustLarger = isOnlyAdjustLarger;
    }

    /**
     * Indicates whether adjustments should be dynamic as the model changes.
     * @param isDynamicAdjustment true for dynamic adjustments, false otherwise
     */
    public void setDynamicAdjustment(boolean isDynamicAdjustment) {
        if (this.isDynamicAdjustment != isDynamicAdjustment) {
            // Add or remove the TableModelListener based on the new setting
            if (isDynamicAdjustment) {
                installTableModelListener();
            } else {
                removeTableModelListener();
            }
        }
        this.isDynamicAdjustment = isDynamicAdjustment;
    }
    // </editor-fold>

    // <editor-fold desc="Listener Installation/Removal">
    private void installListeners() {
        // Listen for table property changes (e.g., model change)
        table.addPropertyChangeListener(this);
        // Listen for component resize events
        table.addComponentListener(this);

        // Install listener for table model changes if dynamic adjustment is enabled
        if (isDynamicAdjustment) {
            installTableModelListener();
        }
    }

    private void installTableModelListener() {
         if (table.getModel() != null) {
             table.getModel().addTableModelListener(tableModelListener);
         }
    }

    private void removeTableModelListener() {
        if (table.getModel() != null) {
            table.getModel().removeTableModelListener(tableModelListener);
        }
    }

    /**
     * Remove listeners from the table. Use when this adjuster is no longer needed.
     */
    public void dispose() {
         table.removePropertyChangeListener(this);
         table.removeComponentListener(this);
         removeTableModelListener();
    }
    // </editor-fold>


    // <editor-fold desc="ComponentListener implementation">
    @Override
    public void componentResized(ComponentEvent e) {
        // Adjust columns when the table component itself is resized
        if (table.isVisible() && table.getWidth() > 0) { // Avoid adjusting when table is not ready
             SwingUtilities.invokeLater(this::adjustColumns);
        }
    }

    @Override
    public void componentMoved(ComponentEvent e) {}

    @Override
    public void componentShown(ComponentEvent e) {
         // Adjust columns when the table becomes visible
         SwingUtilities.invokeLater(this::adjustColumns);
    }

    @Override
    public void componentHidden(ComponentEvent e) {}
    // </editor-fold>


    // <editor-fold desc="PropertyChangeListener implementation">
    @Override
    public void propertyChange(PropertyChangeEvent e) {
        // When the TableModel changes we need to update the listeners and column widths
        if ("model".equals(e.getPropertyName())) {
            TableModel oldModel = (TableModel) e.getOldValue();
            TableModel newModel = (TableModel) e.getNewValue();

            if (oldModel != null && isDynamicAdjustment) {
                oldModel.removeTableModelListener(tableModelListener);
            }
            if (newModel != null && isDynamicAdjustment) {
                newModel.addTableModelListener(tableModelListener);
            }
            // Adjust columns for the new model
            if (table.isVisible()) { // Only adjust if table is visible
                 SwingUtilities.invokeLater(this::adjustColumns);
            }
        }

         // Handle changes to the table header, which might affect rendering
        if ("tableHeader".equals(e.getPropertyName())) {
             if (table.isVisible()) {
                 SwingUtilities.invokeLater(this::adjustColumns);
             }
        }
    }
    // </editor-fold>


    // <editor-fold desc="TableModelListener implementation">
    private final TableModelListener tableModelListener = e -> {
        // A cell or structure has changed, maybe update the width of the affected column(s)
        if (!isDynamicAdjustment) return;

        // Run the adjustment later on the EDT to ensure all model updates are processed
        SwingUtilities.invokeLater(() -> {
            int firstRow = e.getFirstRow();
            int lastRow = e.getLastRow();
            int column = e.getColumn();

            if (column == TableModelEvent.ALL_COLUMNS) {
                 // If the entire table structure might have changed, adjust all columns
                 adjustColumns();
            } else if (e.getType() == TableModelEvent.UPDATE && firstRow != TableModelEvent.HEADER_ROW) {
                 // For cell updates, only adjust the specific column if needed
                 adjustColumn(column);
            } else {
                 // For insertions/deletions or header changes, adjust all columns
                 adjustColumns();
            }
        });
    };
    // </editor-fold>


    // <editor-fold desc="Actions for user control (Optional)">
    /*
     * Install Actions to give user control of certain functionality.
     */
    private void installActions() {
        installColumnAction(true, true, "adjustColumn", "control ADD");
        installColumnAction(false, true, "adjustColumns", "control EQUALS");
        installColumnAction(true, false, "restoreColumn", "control SUBTRACT");
        installColumnAction(false, false, "restoreColumns", "control MULTIPLY");

        installToggleAction(true, false, "toggleDynamic", "control D");
        installToggleAction(false, true, "toggleLarger", "control L");
    }

    /*
     * Update the input and action maps with a new ColumnAction
     */
    private void installColumnAction(
            boolean isSelectedColumn, boolean isAdjust, String key, String keyStroke) {
        Action action = new ColumnAction(isSelectedColumn, isAdjust);
        KeyStroke ks = KeyStroke.getKeyStroke(keyStroke);
        // Use WHEN_FOCUSED to ensure actions work when table has focus
        table.getInputMap(JComponent.WHEN_FOCUSED).put(ks, key);
        table.getActionMap().put(key, action);
    }

    /*
     * Update the input and action maps with new ToggleAction
     */
    private void installToggleAction(
            boolean isToggleDynamic, boolean isToggleLarger, String key, String keyStroke) {
        Action action = new ToggleAction(isToggleDynamic, isToggleLarger);
        KeyStroke ks = KeyStroke.getKeyStroke(keyStroke);
        table.getInputMap(JComponent.WHEN_FOCUSED).put(ks, key);
        table.getActionMap().put(key, action);
    }


    /*
     * Action to adjust or restore the width of a single column or all columns
     */
    private class ColumnAction extends AbstractAction {
        private final boolean isSelectedColumn;
        private final boolean isAdjust;

        public ColumnAction(boolean isSelectedColumn, boolean isAdjust) {
            this.isSelectedColumn = isSelectedColumn;
            this.isAdjust = isAdjust;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (isSelectedColumn) {
                int[] columns = table.getSelectedColumns();
                for (int column : columns) {
                    if (isAdjust)
                        adjustColumn(column);
                    else
                        restoreColumn(column);
                }
            } else {
                if (isAdjust)
                    adjustColumns();
                else
                    restoreColumns();
            }
        }
    }

    /*
     * Toggle properties of the TableColumnAdjuster
     */
    private class ToggleAction extends AbstractAction {
        private final boolean isToggleDynamic;
        private final boolean isToggleLarger;

        public ToggleAction(boolean isToggleDynamic, boolean isToggleLarger) {
            this.isToggleDynamic = isToggleDynamic;
            this.isToggleLarger = isToggleLarger;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (isToggleDynamic) {
                setDynamicAdjustment(!isDynamicAdjustment);
            } else if (isToggleLarger) {
                setOnlyAdjustLarger(!isOnlyAdjustLarger);
            }
            // Optional: Provide feedback to the user about the toggled state
            // System.out.println("Dynamic Adjustment: " + isDynamicAdjustment + ", Only Larger: " + isOnlyAdjustLarger);
        }
    }
    // </editor-fold>
}
