package org.lqzs.sorene;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.CheckBox;
import android.widget.Checkable;
import android.widget.RelativeLayout;

public class ItemLayout extends RelativeLayout implements Checkable {
	private CheckBox checkBox = null;

	public ItemLayout(Context context) {
		super(context);
	}

	public ItemLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public ItemLayout(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Override
	public boolean isChecked() {
		if (checkBox == null) {
			checkBox = findViewById(R.id.checkBox);
		}
		return checkBox.isChecked();
	}

	@Override
	public void setChecked(boolean checked) {
		if (checkBox == null) {
			checkBox = findViewById(R.id.checkBox);
		}
		checkBox.setChecked(checked);
	}

	@Override
	public void toggle() {
		if (checkBox == null) {
			checkBox = findViewById(R.id.checkBox);
		}
		checkBox.setChecked(!checkBox.isChecked());
	}
}
