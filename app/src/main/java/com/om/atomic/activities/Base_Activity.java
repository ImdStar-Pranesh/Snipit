package com.om.atomic.activities;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.KeyEvent;
import android.view.MenuItem;

import com.om.atomic.R;
import com.om.atomic.classes.Constants;
import com.om.atomic.classes.DatabaseHelper;

import icepick.Icepick;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

public class Base_Activity extends ActionBarActivity {
    private String activityName = this.getClass().getSimpleName();
    private DatabaseHelper dbHelper = new DatabaseHelper(this);

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Icepick.restoreInstanceState(this, savedInstanceState);

        performIntroAnimationCheck();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Icepick.saveInstanceState(this, outState);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case android.R.id.home:
                super.onBackPressed();
                performOutroAnimationCheck();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            super.onBackPressed();
            performOutroAnimationCheck();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public void performIntroAnimationCheck() {
        //If Animations are enabled
        if (dbHelper.getParam(null, Constants.ANIMATIONS_ENABLED_DATABASE_VALUE)) {
            //Only set this animation to the Activities that are NOT Paint_Bookmark and NOT Books
            if (!activityName.equals(Constants.BOOKS_ACTIVITY_NAME) && !activityName.equals(Constants.PAINT_BOOKMARK_ACTIVITY_NAME))
                overridePendingTransition(R.anim.right_slide_in, R.anim.right_slide_out);
                //Only set this animation to the Activities that ARE Paint_Bookmark because it takes a different animation than all the above cases (Books also gets a different animation, and that's NO animation) - 3 cases
            else if (!activityName.equals(Constants.BOOKS_ACTIVITY_NAME))
                overridePendingTransition(R.anim.slide_up, R.anim.no_change);
        }
    }

    public void performOutroAnimationCheck() {
        //If Animations are enabled
        if (dbHelper.getParam(null, Constants.ANIMATIONS_ENABLED_DATABASE_VALUE)) {
            //Only set this animation to the Activities that are NOT Paint_Bookmark and NOT Books
            if (!activityName.equals(Constants.BOOKS_ACTIVITY_NAME) && !activityName.equals(Constants.PAINT_BOOKMARK_ACTIVITY_NAME))
                overridePendingTransition(R.anim.right_slide_in_back, R.anim.right_slide_out_back);
                //Only set this animation to the Activities that ARE Paint_Bookmark because it takes a different animation than all the above cases (Books also gets a different animation, and that's NO animation) - 3 cases
            else if (!activityName.equals(Constants.BOOKS_ACTIVITY_NAME))
                overridePendingTransition(R.anim.no_change, R.anim.slide_down);
        }
    }
}
