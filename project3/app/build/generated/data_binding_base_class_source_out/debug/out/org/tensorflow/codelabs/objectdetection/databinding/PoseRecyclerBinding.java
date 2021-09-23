// Generated by view binder compiler. Do not edit!
package org.tensorflow.codelabs.objectdetection.databinding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.viewbinding.ViewBinding;
import androidx.viewbinding.ViewBindings;
import java.lang.NullPointerException;
import java.lang.Override;
import java.lang.String;
import org.tensorflow.codelabs.objectdetection.R;

public final class PoseRecyclerBinding implements ViewBinding {
  @NonNull
  private final ConstraintLayout rootView;

  @NonNull
  public final TextView obj;

  @NonNull
  public final ImageView posePhoto;

  private PoseRecyclerBinding(@NonNull ConstraintLayout rootView, @NonNull TextView obj,
      @NonNull ImageView posePhoto) {
    this.rootView = rootView;
    this.obj = obj;
    this.posePhoto = posePhoto;
  }

  @Override
  @NonNull
  public ConstraintLayout getRoot() {
    return rootView;
  }

  @NonNull
  public static PoseRecyclerBinding inflate(@NonNull LayoutInflater inflater) {
    return inflate(inflater, null, false);
  }

  @NonNull
  public static PoseRecyclerBinding inflate(@NonNull LayoutInflater inflater,
      @Nullable ViewGroup parent, boolean attachToParent) {
    View root = inflater.inflate(R.layout.pose_recycler, parent, false);
    if (attachToParent) {
      parent.addView(root);
    }
    return bind(root);
  }

  @NonNull
  public static PoseRecyclerBinding bind(@NonNull View rootView) {
    // The body of this method is generated in a way you would not otherwise write.
    // This is done to optimize the compiled bytecode for size and performance.
    int id;
    missingId: {
      id = R.id.obj;
      TextView obj = ViewBindings.findChildViewById(rootView, id);
      if (obj == null) {
        break missingId;
      }

      id = R.id.pose_photo;
      ImageView posePhoto = ViewBindings.findChildViewById(rootView, id);
      if (posePhoto == null) {
        break missingId;
      }

      return new PoseRecyclerBinding((ConstraintLayout) rootView, obj, posePhoto);
    }
    String missingId = rootView.getResources().getResourceName(id);
    throw new NullPointerException("Missing required view with ID: ".concat(missingId));
  }
}