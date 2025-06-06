// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.ide.IdeTooltip;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.ide.TooltipEvent;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.EventListenerList;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.EventListener;
import java.util.EventObject;

public class LightweightHint extends UserDataHolderBase implements Hint {
  public static final Key<Boolean> SHOWN_AT_DEBUG = Key.create("shown.at.debug");
  private static final Logger LOG = Logger.getInstance(LightweightHint.class);
  @ApiStatus.Internal
  public static final Key<LightweightHint> POPUP_HINT_KEY = Key.create("POPUP_HINT");
  @ApiStatus.Internal
  public static final Key<Editor> HINT_COMPONENT_EDITOR_KEY = Key.create("HINT_COMPONENT_EDITOR");

  private final @NotNull JComponent myComponent;
  private final EventListenerList myListenerList = new EventListenerList();
  private MyEscListener myEscListener;
  private JBPopup myPopup;
  private JComponent myParentComponent;
  private boolean myIsRealPopup = false;
  private boolean myForceLightweightPopup = false;
  private boolean mySelectingHint;

  private boolean myForceShowAsPopup = false;
  private @NlsContexts.PopupTitle String myTitle = null;
  private boolean myShouldReopenPopup = false;
  private boolean myCancelOnClickOutside = true;
  private boolean myBelongsToGlobalPopupStack = true;
  private boolean myCancelOnOtherWindowOpen = true;
  private boolean myResizable;

  private IdeTooltip myCurrentIdeTooltip;
  private HintHint myHintHint;
  private JComponent myFocusRequestor;

  private boolean myForceHideShadow = false;

  public LightweightHint(final @NotNull JComponent component) {
    myComponent = component;
  }

  public void setForceLightweightPopup(final boolean forceLightweightPopup) {
    myForceLightweightPopup = forceLightweightPopup;
  }


  public void setForceShowAsPopup(final boolean forceShowAsPopup) {
    myForceShowAsPopup = forceShowAsPopup;
    myShouldReopenPopup = true;
  }

  public void setFocusRequestor(JComponent c) {
    myFocusRequestor = c;
  }

  public void setTitle(final @NlsContexts.PopupTitle String title) {
    myTitle = title;
  }

  public boolean isSelectingHint() {
    return mySelectingHint;
  }

  public void setSelectingHint(final boolean selectingHint) {
    mySelectingHint = selectingHint;
  }

  public void setCancelOnClickOutside(final boolean b) {
    myCancelOnClickOutside = b;
  }

  public void setBelongsToGlobalPopupStack(final boolean b) {
    myBelongsToGlobalPopupStack = b;
  }

  public void setCancelOnOtherWindowOpen(final boolean b) {
    myCancelOnOtherWindowOpen = b;
  }

  public void setResizable(final boolean b) {
    myResizable = b;
  }

  protected boolean canAutoHideOn(TooltipEvent event) {
    return true;
  }

  /**
   * Shows the hint in the layered pane. Coordinates {@code x} and {@code y}
   * are in {@code parentComponent} coordinate system. Note that the component
   * appears on 250 layer.
   */
  @Override
  public void show(final @NotNull JComponent parentComponent,
                   final int x,
                   final int y,
                   final JComponent focusBackComponent,
                   final @NotNull HintHint hintHint) {
    myParentComponent = parentComponent;
    myHintHint = hintHint;

    LOG.assertTrue(myParentComponent.isShowing());
    myEscListener = new MyEscListener();
    myComponent.registerKeyboardAction(myEscListener, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    myComponent.registerKeyboardAction(myEscListener, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_FOCUSED);
    final JLayeredPane layeredPane = parentComponent.getRootPane().getLayeredPane();

    Point point = new Point(x, y);
    myComponent.validate();

    HintMode mode;
    if (myForceShowAsPopup || hintHint.isPopupForced()) {
      mode = HintMode.REAL_POPUP;
    }
    else if (hintHint.isAwtTooltip()) {
      mode = HintMode.AWT_TOOLTIP;
    }
    else if (myForceLightweightPopup) {
      mode = HintMode.LAYERED_PANE;
    }
    else {
      boolean fitsLayeredPane = fitsLayeredPane(layeredPane, myComponent, new RelativePoint(parentComponent, point), hintHint);
      mode = fitsLayeredPane ? HintMode.LAYERED_PANE : HintMode.REAL_POPUP;
    }

    switch (mode) {
      case AWT_TOOLTIP -> showAwtTooltip(hintHint);
      case LAYERED_PANE -> showLayeredPaneTooltip(parentComponent, point, layeredPane);
      case REAL_POPUP -> showRealPopup(point, hintHint);
    }
  }

  private enum HintMode {AWT_TOOLTIP, LAYERED_PANE, REAL_POPUP}

  private void showAwtTooltip(@NotNull HintHint hintHint) {
    beforeShow();

    IdeTooltip tooltip =
      new IdeTooltip(hintHint.getOriginalComponent(), hintHint.getOriginalPoint(), myComponent, hintHint, myComponent) {
        @Override
        protected boolean canAutohideOn(TooltipEvent event) {
          if (!LightweightHint.this.canAutoHideOn(event)) {
            return false;
          }
          else if (event.getInputEvent() instanceof MouseEvent) {
            return !(hintHint.isContentActive() && event.isIsEventInsideBalloon());
          }
          else if (event.getAction() != null) {
            return false;
          }
          else {
            return true;
          }
        }

        @Override
        protected void onHidden() {
          fireHintHidden();
          TooltipController.getInstance().resetCurrent();
        }

        @Override
        public boolean canBeDismissedOnTimeout() {
          return false;
        }
      }.setToCenterIfSmall(hintHint.isMayCenterTooltip())
        .setPreferredPosition(hintHint.getPreferredPosition())
        .setHighlighterType(hintHint.isHighlighterType())
        .setTextForeground(hintHint.getTextForeground())
        .setTextBackground(hintHint.getTextBackground())
        .setBorderColor(hintHint.getBorderColor())
        .setBorderInsets(hintHint.getBorderInsets())
        .setFont(hintHint.getTextFont())
        .setCalloutShift(hintHint.getCalloutShift())
        .setPositionChangeShift(hintHint.getPositionChangeX(), hintHint.getPositionChangeY())
        .setExplicitClose(hintHint.isExplicitClose())
        .setRequestFocus(hintHint.isRequestFocus())
        .setHint(true);
    myComponent.validate();
    Border border = hintHint.getComponentBorder();
    if (border != null) {
      tooltip.setComponentBorder(border);
    }

    if (ExperimentalUI.isNewUI()) {
      tooltip.setPointerSize(JBUI.size(16, 8)).setPointerShiftedToStart(true);
    }

    myCurrentIdeTooltip = IdeTooltipManager.getInstance().show(tooltip, hintHint.isShowImmediately(), hintHint.isAnimationEnabled());
  }

  private void showLayeredPaneTooltip(@NotNull JComponent parentComponent,
                                      @NotNull Point point,
                                      @NotNull JLayeredPane layeredPane) {
    beforeShow();
    final Dimension preferredSize = myComponent.getPreferredSize();

    final Point layeredPanePoint = SwingUtilities.convertPoint(parentComponent, point, layeredPane);
    Rectangle popupRect = new Rectangle(layeredPanePoint.x, layeredPanePoint.y, preferredSize.width, preferredSize.height);

    int borderGap = JBUI.scale(3); // leave some pretty gap to the edge of frame
    if (popupRect.getMaxX() > layeredPane.getWidth()) {
      popupRect.width = layeredPane.getWidth() - popupRect.x - borderGap;
    }
    if (popupRect.getMaxY() > layeredPane.getHeight()) {
      popupRect.height = layeredPane.getHeight() - popupRect.y - borderGap;
    }

    myComponent.setBounds(popupRect);
    layeredPane.add(myComponent, JLayeredPane.POPUP_LAYER);

    myComponent.validate();
    myComponent.repaint();
  }

  private void showRealPopup(@NotNull Point actualPoint, @NotNull HintHint hintHint) {
    myIsRealPopup = true;

    if (hintHint.getPreferredPosition() == Balloon.Position.atLeft) {
      int width = myComponent.getPreferredSize().width;
      actualPoint.translate(-width, 0);
    }
    JComponent actualComponent = new OpaquePanel(new BorderLayout());
    actualComponent.add(myComponent, BorderLayout.CENTER);
    if (isAwtTooltip()) {
      actualComponent.setBackground(hintHint.getTextBackground());
      actualComponent.validate();
    }

    myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(actualComponent, myFocusRequestor)
      .setRequestFocus(myFocusRequestor != null || hintHint.isRequestFocus())
      .setFocusable(myFocusRequestor != null || hintHint.isRequestFocus())
      .setResizable(myResizable)
      .setMovable(myTitle != null)
      .setTitle(myTitle)
      .setModalContext(false)
      .setShowShadow(isRealPopup() && !isForceHideShadow())
      .setCancelKeyEnabled(false)
      .setCancelOnClickOutside(myCancelOnClickOutside)
      .setBelongsToGlobalPopupStack(myBelongsToGlobalPopupStack)
      .setCancelCallback(() -> {
        //maybe readaction
        ReadAction.run(this::onPopupCancel);
        return true;
      })
      .setCancelOnOtherWindowOpen(myCancelOnOtherWindowOpen)
      .createPopup();

    beforeShow();
    ClientProperty.put(myPopup.getContent(), POPUP_HINT_KEY, this);
    myPopup.show(new RelativePoint(myParentComponent, new Point(actualPoint.x, actualPoint.y)));
  }

  protected void onPopupCancel() {
    ClientProperty.remove(myPopup.getContent(), POPUP_HINT_KEY);
  }

  private void fixActualPoint(Point actualPoint) {
    if (!isAwtTooltip()) return;
    if (!myIsRealPopup) return;
    if (myForceShowAsPopup) return;

    Dimension size = myComponent.getPreferredSize();
    Balloon.Position position = myHintHint.getPreferredPosition();
    int shift = BalloonImpl.getPointerLength(position, false);
    switch (position) {
      case below -> actualPoint.y += shift;
      case above -> actualPoint.y -= (shift + size.height);
      case atLeft -> actualPoint.x -= (shift + size.width);
      case atRight -> actualPoint.y += shift;
    }
  }

  protected void beforeShow() {
  }

  public boolean vetoesHiding() {
    return false;
  }

  public boolean isForceHideShadow() {
    return myForceHideShadow;
  }

  public void setForceHideShadow(boolean forceHideShadow) {
    myForceHideShadow = forceHideShadow;
  }

  private static boolean fitsLayeredPane(JLayeredPane pane, JComponent component, RelativePoint desiredLocation, HintHint hintHint) {
    if (hintHint.isAwtTooltip()) {
      Dimension size = component.getPreferredSize();
      Dimension paneSize = pane.getSize();

      Point target = desiredLocation.getPointOn(pane).getPoint();
      Balloon.Position pos = hintHint.getPreferredPosition();
      int pointer = BalloonImpl.getPointerLength(pos, false) + BalloonImpl.getNormalInset();
      if (pos == Balloon.Position.above || pos == Balloon.Position.below) {
        boolean heightFit = target.y - size.height - pointer > 0 || target.y + size.height + pointer < paneSize.height;
        return heightFit && size.width + pointer < paneSize.width;
      }
      else {
        boolean widthFit = target.x - size.width - pointer > 0 || target.x + size.width + pointer < paneSize.width;
        return widthFit && size.height + pointer < paneSize.height;
      }
    }
    else {
      final Rectangle lpRect = new Rectangle(pane.getLocationOnScreen().x, pane.getLocationOnScreen().y, pane.getWidth(), pane.getHeight());
      Rectangle componentRect = new Rectangle(desiredLocation.getScreenPoint().x,
                                              desiredLocation.getScreenPoint().y,
                                              component.getPreferredSize().width,
                                              component.getPreferredSize().height);
      return lpRect.contains(componentRect);
    }
  }

  private void fireHintHidden() {
    final EventListener[] listeners = myListenerList.getListeners(HintListener.class);
    for (EventListener listener : listeners) {
      ((HintListener)listener).hintHidden(new EventObject(this));
    }
  }

  /**
   * @return bounds of hint component in the parent component's layered pane coordinate system.
   */
  public final Rectangle getBounds() {
    if (myParentComponent == null) return new Rectangle(0, 0, 0, 0);
    Rectangle bounds = new Rectangle(myComponent.getBounds());
    final JLayeredPane layeredPane = myParentComponent.getRootPane().getLayeredPane();
    return SwingUtilities.convertRectangle(myComponent, bounds, layeredPane);
  }

  @Override
  public boolean isVisible() {
    Boolean shownAtDebug = getUserData(SHOWN_AT_DEBUG);
    if (shownAtDebug != null) return shownAtDebug;

    if (myIsRealPopup) {
      return myPopup != null && myPopup.isVisible();
    }
    if (myCurrentIdeTooltip != null) {
      return myComponent.isShowing() || IdeTooltipManager.getInstance().isQueuedToShow(myCurrentIdeTooltip);
    }
    return myComponent.isShowing();
  }

  public final boolean isRealPopup() {
    return myIsRealPopup || myForceShowAsPopup;
  }

  public final boolean isShouldBeReopen() {
    return myShouldReopenPopup;
  }

  @Override
  @RequiresEdt
  public void hide() {
    hide(false);
  }

  @RequiresEdt
  public void hide(boolean ok) {
    if (isVisible()) {
      if (myIsRealPopup) {
        if (ok) {
          myPopup.closeOk(null);
        }
        else {
          myPopup.cancel();
        }
        myPopup = null;
      }
      else {
        if (myCurrentIdeTooltip != null) {
          IdeTooltip tooltip = myCurrentIdeTooltip;
          myCurrentIdeTooltip = null;
          tooltip.hide();
        }
        else {
          JRootPane rootPane = myComponent.getRootPane();
          JLayeredPane layeredPane = rootPane == null ? null : rootPane.getLayeredPane();
          if (layeredPane != null) {
            Rectangle bounds = myComponent.getBounds();
            layeredPane.remove(myComponent);
            layeredPane.paintImmediately(bounds.x, bounds.y, bounds.width, bounds.height);
          }
        }
      }
    }
    if (myEscListener != null) {
      myComponent.unregisterKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
    }

    TooltipController.getInstance().hide(this);

    myShouldReopenPopup = false;
    fireHintHidden();
  }

  @Override
  public void pack() {
    setSize(myComponent.getPreferredSize());
  }

  public void updateLocation(int x, int y) {
    Point point = new Point(x, y);
    fixActualPoint(point);
    setLocation(new RelativePoint(myParentComponent, point));
  }

  public void updatePosition(Balloon.Position position) {
    if (myHintHint != null) {
      myHintHint.setPreferredPosition(position);
    }
    if (myCurrentIdeTooltip != null) {
      myCurrentIdeTooltip.setPreferredPosition(position);
    }
  }

  public final @NotNull JComponent getComponent() {
    return myComponent;
  }

  @Override
  public final void addHintListener(final @NotNull HintListener listener) {
    myListenerList.add(HintListener.class, listener);
  }

  @Override
  public final void removeHintListener(final @NotNull HintListener listener) {
    myListenerList.remove(HintListener.class, listener);
  }

  public Point getLocationOn(JComponent c) {
    Point location;
    if (isRealPopup() && myPopup != null && !myPopup.isDisposed()) {
      location = myPopup.getLocationOnScreen();
      SwingUtilities.convertPointFromScreen(location, c);
    }
    else {
      if (myCurrentIdeTooltip != null) {
        Point tipPoint = myCurrentIdeTooltip.getPoint();
        Component tipComponent = myCurrentIdeTooltip.getComponent();
        return SwingUtilities.convertPoint(tipComponent, tipPoint, c);
      }
      else {
        location = SwingUtilities.convertPoint(
          myComponent.getParent(),
          myComponent.getLocation(),
          c
        );
      }
    }

    return location;
  }

  @Override
  public void setLocation(@NotNull RelativePoint point) {
    if (isRealPopup()) {
      if (myPopup != null) {
        myPopup.setLocation(point.getScreenPoint());
      }
    }
    else {
      if (myCurrentIdeTooltip != null) {
        myCurrentIdeTooltip.setPoint(point.getPoint());
        myCurrentIdeTooltip.setComponent(point.getComponent());
        IdeTooltipManager.getInstance().show(myCurrentIdeTooltip, true, false);
      }
      else {
        Point targetPoint = point.getPoint(myComponent.getParent());
        myComponent.setLocation(targetPoint);

        myComponent.revalidate();
        myComponent.repaint();
      }
    }
  }

  public void setSize(final Dimension size) {
    if (myIsRealPopup && myPopup != null && !myPopup.isDisposed()) {
      // There is a possible case that a popup wraps target content component into other components which might have borders.
      // That's why we can't just apply component's size to the whole popup. It needs to be adjusted before that.
      JComponent popupContent = myPopup.getContent();
      int widthExpand = 0;
      int heightExpand = 0;
      boolean adjustSize = false;
      JComponent prev = myComponent;
      for (Container c = myComponent.getParent(); c != null; c = c.getParent()) {
        if (c == popupContent) {
          adjustSize = true;
          break;
        }
        if (c instanceof JComponent) {
          Border border = ((JComponent)c).getBorder();
          if (prev != null && border != null) {
            Insets insets = border.getBorderInsets(prev);
            widthExpand += insets.left + insets.right;
            heightExpand += insets.top + insets.bottom;
          }
          prev = (JComponent)c;
        }
        else {
          prev = null;
        }
      }
      Dimension sizeToUse = size;
      if (adjustSize && (widthExpand != 0 || heightExpand != 0)) {
        sizeToUse = new Dimension(size.width + widthExpand, size.height + heightExpand);
      }
      myPopup.setSize(sizeToUse);
    }
    else if (!isAwtTooltip()) {
      myComponent.setSize(size);

      myComponent.revalidate();
      myComponent.repaint();
    }
    else { // isAwtTooltip() case, we have to update Balloon size
      Component c = myComponent;
      while (c != null) {
        if (c.getParent() instanceof JLayeredPane) {
          c.setSize(c.getPreferredSize());
          c.revalidate();
          c.repaint();
          break;
        }
        c = c.getParent();
      }
    }
  }

  public boolean isAwtTooltip() {
    return myHintHint != null && myHintHint.isAwtTooltip();
  }

  public Dimension getSize() {
    return myComponent.getSize();
  }

  public boolean isInsideHint(RelativePoint target) {
    if (!myComponent.isShowing()) return false;

    if (myIsRealPopup) {
      Window wnd = SwingUtilities.getWindowAncestor(myComponent);
      return wnd.getBounds().contains(target.getScreenPoint());
    }
    else if (myCurrentIdeTooltip != null) {
      return myCurrentIdeTooltip.isInside(target);
    }
    else {
      return new Rectangle(myComponent.getLocationOnScreen(), myComponent.getSize()).contains(target.getScreenPoint());
    }
  }

  private final class MyEscListener implements ActionListener {
    @Override
    public void actionPerformed(final ActionEvent e) {
      hide();
    }
  }

  @Override
  public String toString() {
    return getComponent().toString();
  }

  public boolean canControlAutoHide() {
    return myCurrentIdeTooltip != null && myCurrentIdeTooltip.getTipComponent().isShowing();
  }

  public IdeTooltip getCurrentIdeTooltip() {
    return myCurrentIdeTooltip;
  }
}
