/*
@VaadinApache2LicenseForJavaFiles@
 */
package com.vaadin.terminal.gwt.client.ui;

import java.util.ArrayList;
import java.util.List;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Overflow;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.HasHTML;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.Widget;
import com.vaadin.terminal.gwt.client.ApplicationConnection;
import com.vaadin.terminal.gwt.client.BrowserInfo;
import com.vaadin.terminal.gwt.client.LayoutManager;
import com.vaadin.terminal.gwt.client.TooltipInfo;
import com.vaadin.terminal.gwt.client.UIDL;
import com.vaadin.terminal.gwt.client.Util;
import com.vaadin.terminal.gwt.client.VTooltip;

public class VMenuBar extends SimpleFocusablePanel implements
        CloseHandler<PopupPanel>, KeyPressHandler, KeyDownHandler,
        FocusHandler, SubPartAware {

    // The hierarchy of VMenuBar is a bit weird as VMenuBar is the Paintable,
    // used for the root menu but also used for the sub menus.

    /** Set the CSS class name to allow styling. */
    public static final String CLASSNAME = "v-menubar";

    /** For server connections **/
    protected String uidlId;
    protected ApplicationConnection client;

    protected final VMenuBar hostReference = this;
    protected CustomMenuItem moreItem = null;

    // Only used by the root menu bar
    protected VMenuBar collapsedRootItems;

    // Construct an empty command to be used when the item has no command
    // associated
    protected static final Command emptyCommand = null;

    public static final String OPEN_ROOT_MENU_ON_HOWER = "ormoh";

    public static final String ATTRIBUTE_CHECKED = "checked";
    public static final String ATTRIBUTE_ITEM_DESCRIPTION = "description";
    public static final String ATTRIBUTE_ITEM_ICON = "icon";
    public static final String ATTRIBUTE_ITEM_DISABLED = "disabled";
    public static final String ATTRIBUTE_ITEM_STYLE = "style";

    public static final String HTML_CONTENT_ALLOWED = "usehtml";

    /** Widget fields **/
    protected boolean subMenu;
    protected ArrayList<CustomMenuItem> items;
    protected Element containerElement;
    protected VOverlay popup;
    protected VMenuBar visibleChildMenu;
    protected boolean menuVisible = false;
    protected VMenuBar parentMenu;
    protected CustomMenuItem selected;

    boolean enabled = true;

    private String width = "notinited";

    private VLazyExecutor iconLoadedExecutioner = new VLazyExecutor(100,
            new ScheduledCommand() {

                public void execute() {
                    iLayout(true);
                }
            });

    boolean openRootOnHover;

    boolean htmlContentAllowed;

    public VMenuBar() {
        // Create an empty horizontal menubar
        this(false, null);

        // Navigation is only handled by the root bar
        addFocusHandler(this);

        /*
         * Firefox auto-repeat works correctly only if we use a key press
         * handler, other browsers handle it correctly when using a key down
         * handler
         */
        if (BrowserInfo.get().isGecko()) {
            addKeyPressHandler(this);
        } else {
            addKeyDownHandler(this);
        }
    }

    public VMenuBar(boolean subMenu, VMenuBar parentMenu) {

        items = new ArrayList<CustomMenuItem>();
        popup = null;
        visibleChildMenu = null;

        containerElement = getElement();

        if (!subMenu) {
            setStyleName(CLASSNAME);
        } else {
            setStyleName(CLASSNAME + "-submenu");
            this.parentMenu = parentMenu;
        }
        this.subMenu = subMenu;

        sinkEvents(Event.ONCLICK | Event.ONMOUSEOVER | Event.ONMOUSEOUT
                | Event.ONLOAD);

        sinkEvents(VTooltip.TOOLTIP_EVENTS);
    }

    @Override
    protected void onDetach() {
        super.onDetach();
        if (!subMenu) {
            setSelected(null);
            hideChildren();
            menuVisible = false;
        }
    }

    void updateSize() {
        // Take from setWidth
        if (!subMenu) {
            // Only needed for root level menu
            hideChildren();
            setSelected(null);
            menuVisible = false;
        }
    }

    /**
     * Build the HTML content for a menu item.
     * 
     * @param item
     * @return
     */
    protected String buildItemHTML(UIDL item) {
        // Construct html from the text and the optional icon
        StringBuffer itemHTML = new StringBuffer();
        if (item.hasAttribute("separator")) {
            itemHTML.append("<span>---</span>");
        } else {
            // Add submenu indicator
            if (item.getChildCount() > 0) {
                String bgStyle = "";
                itemHTML.append("<span class=\"" + CLASSNAME
                        + "-submenu-indicator\"" + bgStyle + ">&#x25BA;</span>");
            }

            itemHTML.append("<span class=\"" + CLASSNAME
                    + "-menuitem-caption\">");
            if (item.hasAttribute("icon")) {
                itemHTML.append("<img src=\""
                        + Util.escapeAttribute(client.translateVaadinUri(item
                                .getStringAttribute("icon"))) + "\" class=\""
                        + Icon.CLASSNAME + "\" alt=\"\" />");
            }
            String itemText = item.getStringAttribute("text");
            if (!htmlContentAllowed) {
                itemText = Util.escapeHTML(itemText);
            }
            itemHTML.append(itemText);
            itemHTML.append("</span>");
        }
        return itemHTML.toString();
    }

    /**
     * This is called by the items in the menu and it communicates the
     * information to the server
     * 
     * @param clickedItemId
     *            id of the item that was clicked
     */
    public void onMenuClick(int clickedItemId) {
        // Updating the state to the server can not be done before
        // the server connection is known, i.e., before updateFromUIDL()
        // has been called.
        if (uidlId != null && client != null) {
            // Communicate the user interaction parameters to server. This call
            // will initiate an AJAX request to the server.
            client.updateVariable(uidlId, "clickedId", clickedItemId, true);
        }
    }

    /** Widget methods **/

    /**
     * Returns a list of items in this menu
     */
    public List<CustomMenuItem> getItems() {
        return items;
    }

    /**
     * Remove all the items in this menu
     */
    public void clearItems() {
        Element e = getContainerElement();
        while (DOM.getChildCount(e) > 0) {
            DOM.removeChild(e, DOM.getChild(e, 0));
        }
        items.clear();
    }

    /**
     * Returns the containing element of the menu
     * 
     * @return
     */
    @Override
    public Element getContainerElement() {
        return containerElement;
    }

    /**
     * Add a new item to this menu
     * 
     * @param html
     *            items text
     * @param cmd
     *            items command
     * @return the item created
     */
    public CustomMenuItem addItem(String html, Command cmd) {
        CustomMenuItem item = GWT.create(CustomMenuItem.class);
        item.setHTML(html);
        item.setCommand(cmd);

        addItem(item);
        return item;
    }

    /**
     * Add a new item to this menu
     * 
     * @param item
     */
    public void addItem(CustomMenuItem item) {
        if (items.contains(item)) {
            return;
        }
        DOM.appendChild(getContainerElement(), item.getElement());
        item.setParentMenu(this);
        item.setSelected(false);
        items.add(item);
    }

    public void addItem(CustomMenuItem item, int index) {
        if (items.contains(item)) {
            return;
        }
        DOM.insertChild(getContainerElement(), item.getElement(), index);
        item.setParentMenu(this);
        item.setSelected(false);
        items.add(index, item);
    }

    /**
     * Remove the given item from this menu
     * 
     * @param item
     */
    public void removeItem(CustomMenuItem item) {
        if (items.contains(item)) {
            int index = items.indexOf(item);

            DOM.removeChild(getContainerElement(),
                    DOM.getChild(getContainerElement(), index));
            items.remove(index);
        }
    }

    /*
     * @see
     * com.google.gwt.user.client.ui.Widget#onBrowserEvent(com.google.gwt.user
     * .client.Event)
     */
    @Override
    public void onBrowserEvent(Event e) {
        super.onBrowserEvent(e);

        // Handle onload events (icon loaded, size changes)
        if (DOM.eventGetType(e) == Event.ONLOAD) {
            VMenuBar parent = getParentMenu();
            if (parent != null) {
                // The onload event for an image in a popup should be sent to
                // the parent, which owns the popup
                parent.iconLoaded();
            } else {
                // Onload events for images in the root menu are handled by the
                // root menu itself
                iconLoaded();
            }
            return;
        }

        Element targetElement = DOM.eventGetTarget(e);
        CustomMenuItem targetItem = null;
        for (int i = 0; i < items.size(); i++) {
            CustomMenuItem item = items.get(i);
            if (DOM.isOrHasChild(item.getElement(), targetElement)) {
                targetItem = item;
            }
        }

        // Handle tooltips
        if (targetItem == null && client != null) {
            // Handle root menubar tooltips
            client.handleTooltipEvent(e, this);
        } else if (targetItem != null) {
            // Handle item tooltips
            targetItem.onBrowserEvent(e);
        }

        if (targetItem != null) {
            switch (DOM.eventGetType(e)) {

            case Event.ONCLICK:
                if (isEnabled() && targetItem.isEnabled()) {
                    itemClick(targetItem);
                }
                if (subMenu) {
                    // Prevent moving keyboard focus to child menus
                    VMenuBar parent = parentMenu;
                    while (parent.getParentMenu() != null) {
                        parent = parent.getParentMenu();
                    }
                    parent.setFocus(true);
                }

                break;

            case Event.ONMOUSEOVER:
                LazyCloser.cancelClosing();

                if (isEnabled() && targetItem.isEnabled()) {
                    itemOver(targetItem);
                }
                break;

            case Event.ONMOUSEOUT:
                itemOut(targetItem);
                LazyCloser.schedule();
                break;
            }
        } else if (subMenu && DOM.eventGetType(e) == Event.ONCLICK && subMenu) {
            // Prevent moving keyboard focus to child menus
            VMenuBar parent = parentMenu;
            while (parent.getParentMenu() != null) {
                parent = parent.getParentMenu();
            }
            parent.setFocus(true);
        }
    }

    private boolean isEnabled() {
        return enabled;
    }

    private void iconLoaded() {
        iconLoadedExecutioner.trigger();
    }

    /**
     * When an item is clicked
     * 
     * @param item
     */
    public void itemClick(CustomMenuItem item) {
        if (item.getCommand() != null) {
            setSelected(null);

            if (visibleChildMenu != null) {
                visibleChildMenu.hideChildren();
            }

            hideParents(true);
            menuVisible = false;
            Scheduler.get().scheduleDeferred(item.getCommand());

        } else {
            if (item.getSubMenu() != null
                    && item.getSubMenu() != visibleChildMenu) {
                setSelected(item);
                showChildMenu(item);
                menuVisible = true;
            } else if (!subMenu) {
                setSelected(null);
                hideChildren();
                menuVisible = false;
            }
        }
    }

    /**
     * When the user hovers the mouse over the item
     * 
     * @param item
     */
    public void itemOver(CustomMenuItem item) {
        if ((openRootOnHover || subMenu || menuVisible) && !item.isSeparator()) {
            setSelected(item);
            if (!subMenu && openRootOnHover && !menuVisible) {
                menuVisible = true; // start opening menus
                LazyCloser.prepare(this);
            }
        }

        if (menuVisible && visibleChildMenu != item.getSubMenu()
                && popup != null) {
            popup.hide();
        }

        if (menuVisible && item.getSubMenu() != null
                && visibleChildMenu != item.getSubMenu()) {
            showChildMenu(item);
        }
    }

    /**
     * When the mouse is moved away from an item
     * 
     * @param item
     */
    public void itemOut(CustomMenuItem item) {
        if (visibleChildMenu != item.getSubMenu()) {
            hideChildMenu(item);
            setSelected(null);
        } else if (visibleChildMenu == null) {
            setSelected(null);
        }
    }

    /**
     * Used to autoclose submenus when they the menu is in a mode which opens
     * root menus on mouse hover.
     */
    private static class LazyCloser extends Timer {
        static LazyCloser INSTANCE;
        private VMenuBar activeRoot;

        @Override
        public void run() {
            activeRoot.hideChildren();
            activeRoot.setSelected(null);
            activeRoot.menuVisible = false;
            activeRoot = null;
        }

        public static void cancelClosing() {
            if (INSTANCE != null) {
                INSTANCE.cancel();
            }
        }

        public static void prepare(VMenuBar vMenuBar) {
            if (INSTANCE == null) {
                INSTANCE = new LazyCloser();
            }
            if (INSTANCE.activeRoot == vMenuBar) {
                INSTANCE.cancel();
            } else if (INSTANCE.activeRoot != null) {
                INSTANCE.cancel();
                INSTANCE.run();
            }
            INSTANCE.activeRoot = vMenuBar;
        }

        public static void schedule() {
            if (INSTANCE != null && INSTANCE.activeRoot != null) {
                INSTANCE.schedule(750);
            }
        }

    }

    /**
     * Shows the child menu of an item. The caller must ensure that the item has
     * a submenu.
     * 
     * @param item
     */
    public void showChildMenu(CustomMenuItem item) {

        int left = 0;
        int top = 0;
        if (subMenu) {
            left = item.getParentMenu().getAbsoluteLeft()
                    + item.getParentMenu().getOffsetWidth();
            top = item.getAbsoluteTop();
        } else {
            left = item.getAbsoluteLeft();
            top = item.getParentMenu().getAbsoluteTop()
                    + item.getParentMenu().getOffsetHeight();
        }
        showChildMenuAt(item, top, left);
    }

    protected void showChildMenuAt(CustomMenuItem item, int top, int left) {
        final int shadowSpace = 10;

        popup = new VOverlay(true, false, true);
        popup.setStyleName(CLASSNAME + "-popup");
        popup.setWidget(item.getSubMenu());
        popup.addCloseHandler(this);
        popup.addAutoHidePartner(item.getElement());

        // at 0,0 because otherwise IE7 add extra scrollbars (#5547)
        popup.setPopupPosition(0, 0);

        item.getSubMenu().onShow();
        visibleChildMenu = item.getSubMenu();
        item.getSubMenu().setParentMenu(this);

        popup.show();

        if (left + popup.getOffsetWidth() >= RootPanel.getBodyElement()
                .getOffsetWidth() - shadowSpace) {
            if (subMenu) {
                left = item.getParentMenu().getAbsoluteLeft()
                        - popup.getOffsetWidth() - shadowSpace;
            } else {
                left = RootPanel.getBodyElement().getOffsetWidth()
                        - popup.getOffsetWidth() - shadowSpace;
            }
            // Accommodate space for shadow
            if (left < shadowSpace) {
                left = shadowSpace;
            }
        }

        top = adjustPopupHeight(top, shadowSpace);

        popup.setPopupPosition(left, top);

    }

    private int adjustPopupHeight(int top, final int shadowSpace) {
        // Check that the popup will fit the screen
        int availableHeight = RootPanel.getBodyElement().getOffsetHeight()
                - top - shadowSpace;
        int missingHeight = popup.getOffsetHeight() - availableHeight;
        if (missingHeight > 0) {
            // First move the top of the popup to get more space
            // Don't move above top of screen, don't move more than needed
            int moveUpBy = Math.min(top - shadowSpace, missingHeight);

            // Update state
            top -= moveUpBy;
            missingHeight -= moveUpBy;
            availableHeight += moveUpBy;

            if (missingHeight > 0) {
                int contentWidth = visibleChildMenu.getOffsetWidth();

                // If there's still not enough room, limit height to fit and add
                // a scroll bar
                Style style = popup.getElement().getStyle();
                style.setHeight(availableHeight, Unit.PX);
                style.setOverflowY(Overflow.SCROLL);

                // Make room for the scroll bar by adjusting the width of the
                // popup
                style.setWidth(contentWidth + Util.getNativeScrollbarSize(),
                        Unit.PX);
                popup.updateShadowSizeAndPosition();
            }
        }
        return top;
    }

    /**
     * Hides the submenu of an item
     * 
     * @param item
     */
    public void hideChildMenu(CustomMenuItem item) {
        if (visibleChildMenu != null
                && !(visibleChildMenu == item.getSubMenu())) {
            popup.hide();
        }
    }

    /**
     * When the menu is shown.
     */
    public void onShow() {
        // remove possible previous selection
        if (selected != null) {
            selected.setSelected(false);
            selected = null;
        }
        menuVisible = true;
    }

    /**
     * Listener method, fired when this menu is closed
     */
    public void onClose(CloseEvent<PopupPanel> event) {
        hideChildren();
        if (event.isAutoClosed()) {
            hideParents(true);
            menuVisible = false;
        }
        visibleChildMenu = null;
        popup = null;
    }

    /**
     * Recursively hide all child menus
     */
    public void hideChildren() {
        if (visibleChildMenu != null) {
            visibleChildMenu.hideChildren();
            popup.hide();
        }
    }

    /**
     * Recursively hide all parent menus
     */
    public void hideParents(boolean autoClosed) {
        if (visibleChildMenu != null) {
            popup.hide();
            setSelected(null);
            menuVisible = !autoClosed;
        }

        if (getParentMenu() != null) {
            getParentMenu().hideParents(autoClosed);
        }
    }

    /**
     * Returns the parent menu of this menu, or null if this is the top-level
     * menu
     * 
     * @return
     */
    public VMenuBar getParentMenu() {
        return parentMenu;
    }

    /**
     * Set the parent menu of this menu
     * 
     * @param parent
     */
    public void setParentMenu(VMenuBar parent) {
        parentMenu = parent;
    }

    /**
     * Returns the currently selected item of this menu, or null if nothing is
     * selected
     * 
     * @return
     */
    public CustomMenuItem getSelected() {
        return selected;
    }

    /**
     * Set the currently selected item of this menu
     * 
     * @param item
     */
    public void setSelected(CustomMenuItem item) {
        // If we had something selected, unselect
        if (item != selected && selected != null) {
            selected.setSelected(false);
        }
        // If we have a valid selection, select it
        if (item != null) {
            item.setSelected(true);
        }

        selected = item;
    }

    /**
     * 
     * A class to hold information on menu items
     * 
     */
    protected static class CustomMenuItem extends Widget implements HasHTML {

        private ApplicationConnection client;

        protected String html = null;
        protected Command command = null;
        protected VMenuBar subMenu = null;
        protected VMenuBar parentMenu = null;
        protected boolean enabled = true;
        protected boolean isSeparator = false;
        protected boolean checkable = false;
        protected boolean checked = false;

        /**
         * Default menu item {@link Widget} constructor for GWT.create().
         * 
         * Use {@link #setHTML(String)} and {@link #setCommand(Command)} after
         * constructing a menu item.
         */
        public CustomMenuItem() {
            this("", null);
        }

        /**
         * Creates a menu item {@link Widget}.
         * 
         * @param html
         * @param cmd
         * @deprecated use the default constructor and {@link #setHTML(String)}
         *             and {@link #setCommand(Command)} instead
         */
        @Deprecated
        public CustomMenuItem(String html, Command cmd) {
            // We need spans to allow inline-block in IE
            setElement(DOM.createSpan());

            setHTML(html);
            setCommand(cmd);
            setSelected(false);
            setStyleName(CLASSNAME + "-menuitem");

            sinkEvents(VTooltip.TOOLTIP_EVENTS);
        }

        public void setSelected(boolean selected) {
            if (selected && !isSeparator) {
                addStyleDependentName("selected");
                // needed for IE6 to have a single style name to match for an
                // element
                // TODO Can be optimized now that IE6 is not supported any more
                if (checkable) {
                    if (checked) {
                        removeStyleDependentName("selected-unchecked");
                        addStyleDependentName("selected-checked");
                    } else {
                        removeStyleDependentName("selected-checked");
                        addStyleDependentName("selected-unchecked");
                    }
                }
            } else {
                removeStyleDependentName("selected");
                // needed for IE6 to have a single style name to match for an
                // element
                removeStyleDependentName("selected-checked");
                removeStyleDependentName("selected-unchecked");
            }
        }

        public void setChecked(boolean checked) {
            if (checkable && !isSeparator) {
                this.checked = checked;

                if (checked) {
                    addStyleDependentName("checked");
                    removeStyleDependentName("unchecked");
                } else {
                    addStyleDependentName("unchecked");
                    removeStyleDependentName("checked");
                }
            } else {
                this.checked = false;
            }
        }

        public boolean isChecked() {
            return checked;
        }

        public void setCheckable(boolean checkable) {
            if (checkable && !isSeparator) {
                this.checkable = true;
            } else {
                setChecked(false);
                this.checkable = false;
            }
        }

        public boolean isCheckable() {
            return checkable;
        }

        /*
         * setters and getters for the fields
         */

        public void setSubMenu(VMenuBar subMenu) {
            this.subMenu = subMenu;
        }

        public VMenuBar getSubMenu() {
            return subMenu;
        }

        public void setParentMenu(VMenuBar parentMenu) {
            this.parentMenu = parentMenu;
        }

        public VMenuBar getParentMenu() {
            return parentMenu;
        }

        public void setCommand(Command command) {
            this.command = command;
        }

        public Command getCommand() {
            return command;
        }

        public String getHTML() {
            return html;
        }

        public void setHTML(String html) {
            this.html = html;
            DOM.setInnerHTML(getElement(), html);

            // Sink the onload event for any icons. The onload
            // events are handled by the parent VMenuBar.
            Util.sinkOnloadForImages(getElement());
        }

        public String getText() {
            return html;
        }

        public void setText(String text) {
            setHTML(Util.escapeHTML(text));
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
            if (enabled) {
                removeStyleDependentName("disabled");
            } else {
                addStyleDependentName("disabled");
            }
        }

        public boolean isEnabled() {
            return enabled;
        }

        private void setSeparator(boolean separator) {
            isSeparator = separator;
            if (separator) {
                setStyleName(CLASSNAME + "-separator");
            } else {
                setStyleName(CLASSNAME + "-menuitem");
                setEnabled(enabled);
            }
        }

        public boolean isSeparator() {
            return isSeparator;
        }

        public void updateFromUIDL(UIDL uidl, ApplicationConnection client) {
            this.client = client;
            setSeparator(uidl.hasAttribute("separator"));
            setEnabled(!uidl.hasAttribute(ATTRIBUTE_ITEM_DISABLED));

            if (!isSeparator() && uidl.hasAttribute(ATTRIBUTE_CHECKED)) {
                // if the selected attribute is present (either true or false),
                // the item is selectable
                setCheckable(true);
                setChecked(uidl.getBooleanAttribute(ATTRIBUTE_CHECKED));
            } else {
                setCheckable(false);
            }

            if (uidl.hasAttribute(ATTRIBUTE_ITEM_STYLE)) {
                String itemStyle = uidl
                        .getStringAttribute(ATTRIBUTE_ITEM_STYLE);
                addStyleDependentName(itemStyle);
            }

            if (uidl.hasAttribute(ATTRIBUTE_ITEM_DESCRIPTION)) {
                String description = uidl
                        .getStringAttribute(ATTRIBUTE_ITEM_DESCRIPTION);
                TooltipInfo info = new TooltipInfo(description);

                VMenuBar root = findRootMenu();
                client.registerTooltip(root, this, info);
            }
        }

        @Override
        public void onBrowserEvent(Event event) {
            super.onBrowserEvent(event);
            if (client != null) {
                client.handleTooltipEvent(event, findRootMenu(), this);
            }
        }

        private VMenuBar findRootMenu() {
            VMenuBar menubar = getParentMenu();

            // Traverse up until root menu is found
            while (menubar.getParentMenu() != null) {
                menubar = menubar.getParentMenu();
            }

            return menubar;
        }

    }

    /**
     * @author Jouni Koivuviita / Vaadin Ltd.
     */
    public void iLayout() {
        iLayout(false);
        updateSize();
    }

    public void iLayout(boolean iconLoadEvent) {
        // Only collapse if there is more than one item in the root menu and the
        // menu has an explicit size
        if ((getItems().size() > 1 || (collapsedRootItems != null && collapsedRootItems
                .getItems().size() > 0))
                && getElement().getStyle().getProperty("width") != null
                && moreItem != null) {

            // Measure the width of the "more" item
            final boolean morePresent = getItems().contains(moreItem);
            addItem(moreItem);
            final int moreItemWidth = moreItem.getOffsetWidth();
            if (!morePresent) {
                removeItem(moreItem);
            }

            int availableWidth = LayoutManager.get(client).getInnerWidth(
                    getElement());

            // Used width includes the "more" item if present
            int usedWidth = getConsumedWidth();
            int diff = availableWidth - usedWidth;
            removeItem(moreItem);

            if (diff < 0) {
                // Too many items: collapse last items from root menu
                int widthNeeded = usedWidth - availableWidth;
                if (!morePresent) {
                    widthNeeded += moreItemWidth;
                }
                int widthReduced = 0;

                while (widthReduced < widthNeeded && getItems().size() > 0) {
                    // Move last root menu item to collapsed menu
                    CustomMenuItem collapse = getItems().get(
                            getItems().size() - 1);
                    widthReduced += collapse.getOffsetWidth();
                    removeItem(collapse);
                    collapsedRootItems.addItem(collapse, 0);
                }
            } else if (collapsedRootItems.getItems().size() > 0) {
                // Space available for items: expand first items from collapsed
                // menu
                int widthAvailable = diff + moreItemWidth;
                int widthGrowth = 0;

                while (widthAvailable > widthGrowth
                        && collapsedRootItems.getItems().size() > 0) {
                    // Move first item from collapsed menu to the root menu
                    CustomMenuItem expand = collapsedRootItems.getItems()
                            .get(0);
                    collapsedRootItems.removeItem(expand);
                    addItem(expand);
                    widthGrowth += expand.getOffsetWidth();
                    if (collapsedRootItems.getItems().size() > 0) {
                        widthAvailable -= moreItemWidth;
                    }
                    if (widthGrowth > widthAvailable) {
                        removeItem(expand);
                        collapsedRootItems.addItem(expand, 0);
                    } else {
                        widthAvailable = diff + moreItemWidth;
                    }
                }
            }
            if (collapsedRootItems.getItems().size() > 0) {
                addItem(moreItem);
            }
        }

        // If a popup is open we might need to adjust the shadow as well if an
        // icon shown in that popup was loaded
        if (popup != null) {
            // Forces a recalculation of the shadow size
            popup.show();
        }
        if (iconLoadEvent) {
            // Size have changed if the width is undefined
            Util.notifyParentOfSizeChange(this, false);
        }
    }

    private int getConsumedWidth() {
        int w = 0;
        for (CustomMenuItem item : getItems()) {
            if (!collapsedRootItems.getItems().contains(item)) {
                w += item.getOffsetWidth();
            }
        }
        return w;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.google.gwt.event.dom.client.KeyPressHandler#onKeyPress(com.google
     * .gwt.event.dom.client.KeyPressEvent)
     */
    public void onKeyPress(KeyPressEvent event) {
        if (handleNavigation(event.getNativeEvent().getKeyCode(),
                event.isControlKeyDown() || event.isMetaKeyDown(),
                event.isShiftKeyDown())) {
            event.preventDefault();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.google.gwt.event.dom.client.KeyDownHandler#onKeyDown(com.google.gwt
     * .event.dom.client.KeyDownEvent)
     */
    public void onKeyDown(KeyDownEvent event) {
        if (handleNavigation(event.getNativeEvent().getKeyCode(),
                event.isControlKeyDown() || event.isMetaKeyDown(),
                event.isShiftKeyDown())) {
            event.preventDefault();
        }
    }

    /**
     * Get the key that moves the selection upwards. By default it is the up
     * arrow key but by overriding this you can change the key to whatever you
     * want.
     * 
     * @return The keycode of the key
     */
    protected int getNavigationUpKey() {
        return KeyCodes.KEY_UP;
    }

    /**
     * Get the key that moves the selection downwards. By default it is the down
     * arrow key but by overriding this you can change the key to whatever you
     * want.
     * 
     * @return The keycode of the key
     */
    protected int getNavigationDownKey() {
        return KeyCodes.KEY_DOWN;
    }

    /**
     * Get the key that moves the selection left. By default it is the left
     * arrow key but by overriding this you can change the key to whatever you
     * want.
     * 
     * @return The keycode of the key
     */
    protected int getNavigationLeftKey() {
        return KeyCodes.KEY_LEFT;
    }

    /**
     * Get the key that moves the selection right. By default it is the right
     * arrow key but by overriding this you can change the key to whatever you
     * want.
     * 
     * @return The keycode of the key
     */
    protected int getNavigationRightKey() {
        return KeyCodes.KEY_RIGHT;
    }

    /**
     * Get the key that selects a menu item. By default it is the Enter key but
     * by overriding this you can change the key to whatever you want.
     * 
     * @return
     */
    protected int getNavigationSelectKey() {
        return KeyCodes.KEY_ENTER;
    }

    /**
     * Get the key that closes the menu. By default it is the escape key but by
     * overriding this yoy can change the key to whatever you want.
     * 
     * @return
     */
    protected int getCloseMenuKey() {
        return KeyCodes.KEY_ESCAPE;
    }

    /**
     * Handles the keyboard events handled by the MenuBar
     * 
     * @param event
     *            The keyboard event received
     * @return true iff the navigation event was handled
     */
    public boolean handleNavigation(int keycode, boolean ctrl, boolean shift) {

        // If tab or shift+tab close menus
        if (keycode == KeyCodes.KEY_TAB) {
            setSelected(null);
            hideChildren();
            menuVisible = false;
            return false;
        }

        if (ctrl || shift || !isEnabled()) {
            // Do not handle tab key, nor ctrl keys
            return false;
        }

        if (keycode == getNavigationLeftKey()) {
            if (getSelected() == null) {
                // If nothing is selected then select the last item
                setSelected(items.get(items.size() - 1));
                if (getSelected().isSeparator() || !getSelected().isEnabled()) {
                    handleNavigation(keycode, ctrl, shift);
                }
            } else if (visibleChildMenu == null && getParentMenu() == null) {
                // If this is the root menu then move to the right
                int idx = items.indexOf(getSelected());
                if (idx > 0) {
                    setSelected(items.get(idx - 1));
                } else {
                    setSelected(items.get(items.size() - 1));
                }

                if (getSelected().isSeparator() || !getSelected().isEnabled()) {
                    handleNavigation(keycode, ctrl, shift);
                }
            } else if (visibleChildMenu != null) {
                // Redirect all navigation to the submenu
                visibleChildMenu.handleNavigation(keycode, ctrl, shift);

            } else if (getParentMenu().getParentMenu() == null) {

                // Get the root menu
                VMenuBar root = getParentMenu();

                root.getSelected().getSubMenu().setSelected(null);
                root.hideChildren();

                // Get the root menus items and select the previous one
                int idx = root.getItems().indexOf(root.getSelected());
                idx = idx > 0 ? idx : root.getItems().size();
                CustomMenuItem selected = root.getItems().get(--idx);

                while (selected.isSeparator() || !selected.isEnabled()) {
                    idx = idx > 0 ? idx : root.getItems().size();
                    selected = root.getItems().get(--idx);
                }

                root.setSelected(selected);
                root.showChildMenu(selected);
                VMenuBar submenu = selected.getSubMenu();

                // Select the first item in the newly open submenu
                submenu.setSelected(submenu.getItems().get(0));

            } else {
                getParentMenu().getSelected().getSubMenu().setSelected(null);
                getParentMenu().hideChildren();
            }

            return true;

        } else if (keycode == getNavigationRightKey()) {

            if (getSelected() == null) {
                // If nothing is selected then select the first item
                setSelected(items.get(0));
                if (getSelected().isSeparator() || !getSelected().isEnabled()) {
                    handleNavigation(keycode, ctrl, shift);
                }
            } else if (visibleChildMenu == null && getParentMenu() == null) {
                // If this is the root menu then move to the right
                int idx = items.indexOf(getSelected());

                if (idx < items.size() - 1) {
                    setSelected(items.get(idx + 1));
                } else {
                    setSelected(items.get(0));
                }

                if (getSelected().isSeparator() || !getSelected().isEnabled()) {
                    handleNavigation(keycode, ctrl, shift);
                }
            } else if (visibleChildMenu == null
                    && getSelected().getSubMenu() != null) {
                // If the item has a submenu then show it and move the selection
                // there
                showChildMenu(getSelected());
                menuVisible = true;
                visibleChildMenu.handleNavigation(keycode, ctrl, shift);
            } else if (visibleChildMenu == null) {

                // Get the root menu
                VMenuBar root = getParentMenu();
                while (root.getParentMenu() != null) {
                    root = root.getParentMenu();
                }

                // Hide the submenu
                root.hideChildren();

                // Get the root menus items and select the next one
                int idx = root.getItems().indexOf(root.getSelected());
                idx = idx < root.getItems().size() - 1 ? idx : -1;
                CustomMenuItem selected = root.getItems().get(++idx);

                while (selected.isSeparator() || !selected.isEnabled()) {
                    idx = idx < root.getItems().size() - 1 ? idx : -1;
                    selected = root.getItems().get(++idx);
                }

                root.setSelected(selected);
                root.showChildMenu(selected);
                VMenuBar submenu = selected.getSubMenu();

                // Select the first item in the newly open submenu
                submenu.setSelected(submenu.getItems().get(0));

            } else if (visibleChildMenu != null) {
                // Redirect all navigation to the submenu
                visibleChildMenu.handleNavigation(keycode, ctrl, shift);
            }

            return true;

        } else if (keycode == getNavigationUpKey()) {

            if (getSelected() == null) {
                // If nothing is selected then select the last item
                setSelected(items.get(items.size() - 1));
                if (getSelected().isSeparator() || !getSelected().isEnabled()) {
                    handleNavigation(keycode, ctrl, shift);
                }
            } else if (visibleChildMenu != null) {
                // Redirect all navigation to the submenu
                visibleChildMenu.handleNavigation(keycode, ctrl, shift);
            } else {
                // Select the previous item if possible or loop to the last item
                int idx = items.indexOf(getSelected());
                if (idx > 0) {
                    setSelected(items.get(idx - 1));
                } else {
                    setSelected(items.get(items.size() - 1));
                }

                if (getSelected().isSeparator() || !getSelected().isEnabled()) {
                    handleNavigation(keycode, ctrl, shift);
                }
            }

            return true;

        } else if (keycode == getNavigationDownKey()) {

            if (getSelected() == null) {
                // If nothing is selected then select the first item
                setSelected(items.get(0));
                if (getSelected().isSeparator() || !getSelected().isEnabled()) {
                    handleNavigation(keycode, ctrl, shift);
                }
            } else if (visibleChildMenu == null && getParentMenu() == null) {
                // If this is the root menu the show the child menu with arrow
                // down
                showChildMenu(getSelected());
                menuVisible = true;
                visibleChildMenu.handleNavigation(keycode, ctrl, shift);
            } else if (visibleChildMenu != null) {
                // Redirect all navigation to the submenu
                visibleChildMenu.handleNavigation(keycode, ctrl, shift);
            } else {
                // Select the next item if possible or loop to the first item
                int idx = items.indexOf(getSelected());
                if (idx < items.size() - 1) {
                    setSelected(items.get(idx + 1));
                } else {
                    setSelected(items.get(0));
                }

                if (getSelected().isSeparator() || !getSelected().isEnabled()) {
                    handleNavigation(keycode, ctrl, shift);
                }
            }
            return true;

        } else if (keycode == getCloseMenuKey()) {
            setSelected(null);
            hideChildren();
            menuVisible = false;

        } else if (keycode == getNavigationSelectKey()) {
            if (visibleChildMenu != null) {
                // Redirect all navigation to the submenu
                visibleChildMenu.handleNavigation(keycode, ctrl, shift);
                menuVisible = false;
            } else if (visibleChildMenu == null
                    && getSelected().getSubMenu() != null) {
                // If the item has a submenu then show it and move the selection
                // there
                showChildMenu(getSelected());
                menuVisible = true;
                visibleChildMenu.handleNavigation(keycode, ctrl, shift);
            } else {
                Command command = getSelected().getCommand();
                if (command != null) {
                    command.execute();
                }

                setSelected(null);
                hideParents(true);
            }
        }

        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.google.gwt.event.dom.client.FocusHandler#onFocus(com.google.gwt.event
     * .dom.client.FocusEvent)
     */
    public void onFocus(FocusEvent event) {

    }

    private final String SUBPART_PREFIX = "item";

    public Element getSubPartElement(String subPart) {
        int index = Integer
                .parseInt(subPart.substring(SUBPART_PREFIX.length()));
        CustomMenuItem item = getItems().get(index);

        return item.getElement();
    }

    public String getSubPartName(Element subElement) {
        if (!getElement().isOrHasChild(subElement)) {
            return null;
        }

        Element menuItemRoot = subElement;
        while (menuItemRoot != null && menuItemRoot.getParentElement() != null
                && menuItemRoot.getParentElement() != getElement()) {
            menuItemRoot = menuItemRoot.getParentElement().cast();
        }
        // "menuItemRoot" is now the root of the menu item

        final int itemCount = getItems().size();
        for (int i = 0; i < itemCount; i++) {
            if (getItems().get(i).getElement() == menuItemRoot) {
                String name = SUBPART_PREFIX + i;
                return name;
            }
        }
        return null;
    }

}
