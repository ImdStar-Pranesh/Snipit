package com.om.snipit.activities;

import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.Toolbar;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.ImageView;

import com.andreabaccega.widget.FormEditText;
import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.om.snipit.R;
import com.om.snipit.classes.Constants;
import com.om.snipit.classes.DatabaseHelper;
import com.om.snipit.classes.EventBus_Poster;
import com.om.snipit.classes.EventBus_Singleton;
import com.om.snipit.classes.Helper_Methods;
import com.om.snipit.models.Book;
import com.om.snipit.models.Snippet;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;

import butterknife.ButterKnife;
import butterknife.Bind;
import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;

public class Create_Snippet_Activity extends Base_Activity {

    @Bind(R.id.nameET)
    FormEditText nameET;
    @Bind(R.id.pageNumberET)
    FormEditText pageNumberET;
    @Bind(R.id.snippetIMG)
    ImageView snippetIMG;
    @Bind(R.id.doneBTN)
    FloatingActionButton doneBTN;
    @Bind(R.id.toolbar)
    Toolbar toolbar;

    private Book book;

    private ArrayList<FormEditText> allFields = new ArrayList<>();

    private DatabaseHelper databaseHelper;
    private RuntimeExceptionDao<Snippet, Integer> snippetDAO;

    private int CALL_PURPOSE;
    private Snippet snippet_from_list;
    private String snippetImagePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_snippet);

        ButterKnife.bind(this);

        snippetDAO = getHelper().getSnippetDAO();

        allFields.add(nameET);

        final Helper_Methods helperMethods = new Helper_Methods(this);

        EventBus_Singleton.getInstance().register(this);

        book = getIntent().getParcelableExtra(Constants.EXTRAS_BOOK);

        setSupportActionBar(toolbar);
        helperMethods.setUpActionbarColors(this, book.getColorCode());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            toolbar.setElevation(25f);
        }

        CALL_PURPOSE = getIntent().getIntExtra(Constants.EDIT_SNIPPET_PURPOSE_STRING, -1);

        snippetImagePath = getIntent().getExtras().getString(Constants.EXTRAS_SNIPPET_TEMP_IMAGE_PATH);

        //If it is a create operation, the path to the snippet image is inside the extras that were sent to this activity (from Camera intent)
        try {
            Picasso.with(this).load(new File(snippetImagePath)).resize(500, 500).centerInside().into(snippetIMG);
        } catch (NullPointerException NPE) {
            NPE.printStackTrace();
        }

        //If it's an edit operation, the path to the snippet image is inside the object being sent to this activity
        if (CALL_PURPOSE == Constants.EDIT_SNIPPET_PURPOSE_VALUE) {
            getSupportActionBar().setTitle(getString(R.string.edit_snippet_activity_title));

            snippet_from_list = getIntent().getParcelableExtra(Constants.EXTRAS_SNIPPET);

            nameET.setText(snippet_from_list.getName());
            nameET.setSelection(nameET.getText().length());

            if (snippet_from_list.getPage_number() != Constants.NO_SNIPPET_PAGE_NUMBER)
                pageNumberET.setText(String.valueOf(snippet_from_list.getPage_number()));

            Picasso.with(this).load(new File(snippet_from_list.getImage_path())).into(snippetIMG);
        } else {
            getSupportActionBar().setTitle(getString(R.string.create_snippet_activity_title));
        }

        doneBTN.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (helperMethods.validateFields(allFields)) {
                    //If you are editing an existing snippet
                    if (CALL_PURPOSE == Constants.EDIT_SNIPPET_PURPOSE_VALUE) {
                        try {
                            snippet_from_list.setName(nameET.getText().toString());

                            //Only try to parse if there was a number given
                            if (!pageNumberET.getText().toString().isEmpty())
                                snippet_from_list.setPage_number(Short.parseShort(pageNumberET.getText().toString()));
                            else
                                snippet_from_list.setPage_number(Constants.NO_SNIPPET_PAGE_NUMBER);

                            snippet_from_list.setBook(book);

                            snippetDAO.update(snippet_from_list);

                            EventBus_Singleton.getInstance().post(new EventBus_Poster("snippet_name_page_edited"));

                            finish();
                        } catch (NumberFormatException e) {
                            pageNumberET.setText("");
                            Crouton.makeText(Create_Snippet_Activity.this, getString(R.string.page_number_error), Style.ALERT).show();
                        }
                    } else {
                        //If you are creating a new snippet
                        Date date = new Date();
                        String month = (String) DateFormat.format("MMM", date);
                        String day = (String) DateFormat.format("dd", date);
                        String year = (String) DateFormat.format("yyyy", date);

                        try {
                            Snippet snippet = new Snippet();
                            snippet.setName(nameET.getText().toString());
                            snippet.setOrder(snippetDAO.queryForEq("book_id", book.getId()).size() + 1);

                            //Only try to parse if there was a number given
                            if (!pageNumberET.getText().toString().isEmpty())
                                snippet.setPage_number(Short.parseShort(pageNumberET.getText().toString()));
                            else
                                snippet.setPage_number(Constants.NO_SNIPPET_PAGE_NUMBER);

                            snippet.setImage_path(snippetImagePath);

                            snippet.setDate_added(month + " " + day + ", " + year);

                            snippet.setBook(book);

                            int snippet_id = snippetDAO.create(snippet);

                            Helper_Methods.logEvent("Created Snippet", new String[]{snippet.getName()});

                            EventBus_Singleton.getInstance().post(new EventBus_Poster("snippet_added_snippets_activity", String.valueOf(snippet_id)));
                            EventBus_Singleton.getInstance().post(new EventBus_Poster("snippet_added_books_activity"));

                            finish();
                        } catch (NumberFormatException e) {
                            pageNumberET.setText("");
                            Crouton.makeText(Create_Snippet_Activity.this, getString(R.string.page_number_error), Style.ALERT).show();
                        }
                    }
                }
            }
        });
    }

    public DatabaseHelper getHelper() {
        if (databaseHelper == null) {
            databaseHelper =
                    OpenHelperManager.getHelper(this, DatabaseHelper.class);
        }

        return databaseHelper;
    }
}
