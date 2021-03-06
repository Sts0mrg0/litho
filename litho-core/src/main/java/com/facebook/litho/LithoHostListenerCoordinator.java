/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.litho;

import android.graphics.Rect;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.facebook.litho.stats.LithoStats;
import com.facebook.rendercore.MountDelegateTarget;
import com.facebook.rendercore.RenderUnit;
import com.facebook.rendercore.extensions.MountExtension;
import com.facebook.rendercore.incrementalmount.IncrementalMountExtension;
import com.facebook.rendercore.visibility.VisibilityMountExtension;
import java.util.ArrayList;
import java.util.List;

/** Helper for dispatching events to multiple MountListenerExtensions in Litho. */
public class LithoHostListenerCoordinator extends MountExtension<Object> {

  private final List<MountExtension> mMountExtensions;
  private IncrementalMountExtension mIncrementalMountExtension;
  private VisibilityMountExtension mVisibilityExtension;
  private TransitionsExtension mTransitionsExtension;
  private EndToEndTestingExtension mEndToEndTestingExtension;
  private DynamicPropsBinder mDynamicPropsBinder;

  private @Nullable List<RenderUnit.Binder<LithoRenderUnit, Object>> mMountUnmountExtensions;
  private @Nullable List<RenderUnit.Binder<LithoRenderUnit, Object>> mAttachDetachExtensions;
  private @Nullable LithoRenderUnitFactory mLithoRenderUnitFactory;

  public LithoHostListenerCoordinator() {
    mMountExtensions = new ArrayList<>();
  }

  // TODO figure out how to better enforce the input type here.
  @Override
  public void beforeMount(Object input, Rect localVisibleRect) {
    for (int i = 0, size = mMountExtensions.size(); i < size; i++) {
      MountExtension hostListenerExtension = mMountExtensions.get(i);
      hostListenerExtension.beforeMount(input, localVisibleRect);
    }
  }

  @Override
  public void afterMount() {
    for (int i = 0, size = mMountExtensions.size(); i < size; i++) {
      mMountExtensions.get(i).afterMount();
    }
  }

  @Override
  public void onVisibleBoundsChanged(Rect localVisibleRect) {
    // We first mount and then we process visibility outputs.
    if (mIncrementalMountExtension != null) {
      mIncrementalMountExtension.onVisibleBoundsChanged(localVisibleRect);
      LithoStats.incrementComponentMountCount();
    }

    if (mTransitionsExtension != null) {
      mTransitionsExtension.onVisibleBoundsChanged(localVisibleRect);
    }

    if (mVisibilityExtension != null) {
      mVisibilityExtension.onVisibleBoundsChanged(localVisibleRect);
    }
  }

  @Override
  public void onUnmount() {
    for (int i = 0, size = mMountExtensions.size(); i < size; i++) {
      mMountExtensions.get(i).onUnmount();
    }
  }

  @Override
  public void onUnbind() {
    for (int i = 0, size = mMountExtensions.size(); i < size; i++) {
      mMountExtensions.get(i).onUnbind();
    }
  }

  void enableIncrementalMount(LithoView lithoView, MountDelegateTarget mountDelegateTarget) {
    if (mIncrementalMountExtension != null) {
      throw new IllegalStateException(
          "Incremental mount has already been enabled on this coordinator.");
    }

    mIncrementalMountExtension =
        new IncrementalMountExtension(
            mountDelegateTarget instanceof com.facebook.rendercore.MountState);

    mountDelegateTarget.registerMountDelegateExtension(mIncrementalMountExtension);
    registerListener(mIncrementalMountExtension);
    addAttachDetachExtension(mIncrementalMountExtension.getAttachDetachBinder());
  }

  void enableVisibilityProcessing(LithoView lithoView, MountDelegateTarget mountDelegateTarget) {
    if (mVisibilityExtension != null) {
      throw new IllegalStateException(
          "Visibility processing has already been enabled on this coordinator");
    }

    mVisibilityExtension = new VisibilityMountExtension();
    mountDelegateTarget.registerMountDelegateExtension(mVisibilityExtension);
    registerListener(mVisibilityExtension);
  }

  void enableEndToEndTestProcessing(MountDelegateTarget mountDelegateTarget) {
    if (mEndToEndTestingExtension != null) {
      throw new IllegalStateException(
          "End to end test processing has already been enabled on this coordinator");
    }

    mEndToEndTestingExtension = new EndToEndTestingExtension(mountDelegateTarget);
    registerListener(mEndToEndTestingExtension);
  }

  @Nullable
  VisibilityMountExtension getVisibilityExtension() {
    return mVisibilityExtension;
  }

  @Nullable
  EndToEndTestingExtension getEndToEndTestingExtension() {
    return mEndToEndTestingExtension;
  }

  void enableTransitions(LithoView lithoView, MountDelegateTarget mountDelegateTarget) {
    if (mTransitionsExtension != null) {
      throw new IllegalStateException("Transitions have already been enabled on this coordinator.");
    }

    mTransitionsExtension = new TransitionsExtension(lithoView);
    mountDelegateTarget.registerMountDelegateExtension(mTransitionsExtension);
    registerListener(mTransitionsExtension);
    addAttachDetachExtension(mTransitionsExtension.getAttachDetachBinder());
    addMountUnmountExtension(mTransitionsExtension.getMountUnmountBinder());
  }

  void collectAllTransitions(LayoutState layoutState, ComponentTree componentTree) {
    if (mTransitionsExtension == null) {
      return;
    }

    mTransitionsExtension.collectAllTransitions(layoutState, componentTree);
  }

  private void registerListener(MountExtension mountListenerExtension) {
    mMountExtensions.add(mountListenerExtension);
  }

  @VisibleForTesting
  void useVisibilityExtension(VisibilityMountExtension extension) {
    mVisibilityExtension = extension;
    registerListener(mVisibilityExtension);
  }

  public void enableDynamicProps() {
    if (mDynamicPropsBinder != null) {
      return;
    }

    mDynamicPropsBinder = new DynamicPropsBinder();
    addAttachDetachExtension(mDynamicPropsBinder);
  }

  private void addAttachDetachExtension(RenderUnit.Binder attachDetachExtension) {
    if (mAttachDetachExtensions == null) {
      mAttachDetachExtensions = new ArrayList<>(2);
    }

    mAttachDetachExtensions.add(attachDetachExtension);
  }

  private void addMountUnmountExtension(RenderUnit.Binder mountUnmountExtension) {
    if (mMountUnmountExtensions == null) {
      mMountUnmountExtensions = new ArrayList<>(2);
    }

    mMountUnmountExtensions.add(mountUnmountExtension);
  }

  public @Nullable LithoRenderUnitFactory getLithoRenderUnitFactory(boolean delegateToRenderCore) {
    if (mLithoRenderUnitFactory == null) {
      mLithoRenderUnitFactory =
          new LithoRenderUnitFactory(
              mMountUnmountExtensions, mAttachDetachExtensions, delegateToRenderCore);
    }

    return mLithoRenderUnitFactory;
  }
}
