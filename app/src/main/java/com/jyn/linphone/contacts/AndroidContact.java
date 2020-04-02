/*
 * Copyright (c) 2010-2019 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.jyn.linphone.contacts;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import com.jyn.linphone.LinphoneContext;
import com.jyn.linphone.R;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import org.linphone.core.tools.Log;

class AndroidContact implements Serializable {
    String mAndroidId;
    private String mAndroidRawId;
    private boolean isAndroidRawIdLinphone;
    private transient ArrayList<ContentProviderOperation> mChangesToCommit;
    private byte[] mTempPicture;

    AndroidContact() {
        mChangesToCommit = new ArrayList<>();
        isAndroidRawIdLinphone = false;
        mTempPicture = null;
    }

    String getAndroidId() {
        return mAndroidId;
    }

    void setAndroidId(String id) {
        mAndroidId = id;
    }

    boolean isAndroidContact() {
        return mAndroidId != null;
    }

    private void addChangesToCommit(ContentProviderOperation operation) {
        Log.i("[Contact] Added operation " + operation);
        if (mChangesToCommit == null) {
            mChangesToCommit = new ArrayList<>();
        }
        mChangesToCommit.add(operation);
    }

    void saveChangesCommited() {
        if (ContactsManager.getInstance().hasReadContactsAccess() && mChangesToCommit.size() > 0) {
            try {
                ContentResolver contentResolver =
                        LinphoneContext.instance().getApplicationContext().getContentResolver();
                ContentProviderResult[] results =
                        contentResolver.applyBatch(ContactsContract.AUTHORITY, mChangesToCommit);
                if (results != null
                        && results.length > 0
                        && results[0] != null
                        && results[0].uri != null) {
                    String rawId = String.valueOf(ContentUris.parseId(results[0].uri));
                    if (mAndroidId == null) {
                        Log.i("[Contact] Contact created with RAW ID " + rawId);
                        mAndroidRawId = rawId;
                        if (mTempPicture != null) {
                            Log.i(
                                    "[Contact] Contact has been created, raw is is available, time to set the photo");
                            setPhoto(mTempPicture);
                        }

                        final String[] projection = new String[] {RawContacts.CONTACT_ID};
                        final Cursor cursor =
                                contentResolver.query(results[0].uri, projection, null, null, null);
                        if (cursor != null) {
                            cursor.moveToNext();
                            long contactId = cursor.getLong(0);
                            mAndroidId = String.valueOf(contactId);
                            cursor.close();
                            Log.i("[Contact] Contact created with ID " + mAndroidId);
                        }
                    } else {
                        if (mAndroidRawId == null || !isAndroidRawIdLinphone) {
                            Log.i(
                                    "[Contact] Linphone RAW ID "
                                            + rawId
                                            + " created from existing RAW ID "
                                            + mAndroidRawId);
                            mAndroidRawId = rawId;
                            isAndroidRawIdLinphone = true;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("[Contact] Exception while saving changes: " + e);
            } finally {
                mChangesToCommit.clear();
            }
        }
    }

    void createAndroidContact() {
        if (LinphoneContext.instance()
                .getApplicationContext()
                .getResources()
                .getBoolean(R.bool.use_linphone_tag)) {
            Log.i("[Contact] Creating contact using linphone account type");
            addChangesToCommit(
                    ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                            .withValue(
                                    RawContacts.ACCOUNT_TYPE,
                                    ContactsManager.getInstance()
                                            .getString(R.string.sync_account_type))
                            .withValue(
                                    RawContacts.ACCOUNT_NAME,
                                    ContactsManager.getInstance()
                                            .getString(R.string.sync_account_name))
                            .withValue(
                                    RawContacts.AGGREGATION_MODE,
                                    RawContacts.AGGREGATION_MODE_DEFAULT)
                            .build());
            isAndroidRawIdLinphone = true;

        } else {
            Log.i("[Contact] Creating contact using default account type");
            addChangesToCommit(
                    ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                            .withValue(RawContacts.ACCOUNT_TYPE, null)
                            .withValue(RawContacts.ACCOUNT_NAME, null)
                            .withValue(
                                    RawContacts.AGGREGATION_MODE,
                                    RawContacts.AGGREGATION_MODE_DEFAULT)
                            .build());
        }
    }

    void deleteAndroidContact() {
        Log.i("[Contact] Deleting Android contact ", this);
        ContactsManager.getInstance().delete(mAndroidId);
    }

    Uri getContactThumbnailPictureUri() {
        Uri person = ContentUris.withAppendedId(Contacts.CONTENT_URI, Long.parseLong(mAndroidId));
        return Uri.withAppendedPath(person, Contacts.Photo.CONTENT_DIRECTORY);
    }

    Uri getContactPictureUri() {
        Uri person = ContentUris.withAppendedId(Contacts.CONTENT_URI, Long.parseLong(mAndroidId));
        return Uri.withAppendedPath(person, Contacts.Photo.DISPLAY_PHOTO);
    }

    void setName(String fn, String ln) {
        if ((fn == null || fn.isEmpty()) && (ln == null || ln.isEmpty())) {
            Log.e("[Contact] Can't set both first and last name to null or empty");
            return;
        }

        if (mAndroidId == null) {
            Log.i("[Contact] Setting given & family name " + fn + " " + ln + " to new contact.");
            addChangesToCommit(
                    ContentProviderOperation.newInsert(Data.CONTENT_URI)
                            .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                            .withValue(
                                    Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                            .withValue(CommonDataKinds.StructuredName.GIVEN_NAME, fn)
                            .withValue(CommonDataKinds.StructuredName.FAMILY_NAME, ln)
                            .build());
        } else {
            Log.i(
                    "[Contact] Setting given & family name "
                            + fn
                            + " "
                            + ln
                            + " to existing contact "
                            + mAndroidId
                            + " ("
                            + mAndroidRawId
                            + ")");
            String select = Data.CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?";
            String[] args =
                    new String[] {getAndroidId(), CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE};

            addChangesToCommit(
                    ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                            .withSelection(select, args)
                            .withValue(
                                    Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                            .withValue(CommonDataKinds.StructuredName.GIVEN_NAME, fn)
                            .withValue(CommonDataKinds.StructuredName.FAMILY_NAME, ln)
                            .build());
        }
    }

    boolean isLinphoneAddressMimeEntryAlreadyExisting(String value) {

        return true;
    }

    void removeNumberOrAddress(String noa, boolean isSIP) {}

    void setOrganization(String org, String previousValue) {
        if (org == null || org.isEmpty()) {
            if (mAndroidId == null) {
                Log.e("[Contact] Can't set organization to null or empty for new contact");
                return;
            }
        }
        if (mAndroidId == null) {
            Log.i("[Contact] Setting organization " + org + " to new contact.");
            addChangesToCommit(
                    ContentProviderOperation.newInsert(Data.CONTENT_URI)
                            .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                            .withValue(
                                    Data.MIMETYPE, CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                            .withValue(CommonDataKinds.Organization.COMPANY, org)
                            .build());
        } else {
            if (previousValue != null) {
                String select =
                        Data.CONTACT_ID
                                + "=? AND "
                                + Data.MIMETYPE
                                + "=? AND "
                                + CommonDataKinds.Organization.COMPANY
                                + "=?";
                String[] args =
                        new String[] {
                            getAndroidId(),
                            CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
                            previousValue
                        };

                Log.i(
                        "[Contact] Updating organization "
                                + org
                                + " to existing contact "
                                + mAndroidId
                                + " ("
                                + mAndroidRawId
                                + ")");
                addChangesToCommit(
                        ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                                .withSelection(select, args)
                                .withValue(
                                        Data.MIMETYPE,
                                        CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                                .withValue(CommonDataKinds.Organization.COMPANY, org)
                                .build());
            } else {
                Log.i(
                        "[Contact] Setting organization "
                                + org
                                + " to existing contact "
                                + mAndroidId
                                + " ("
                                + mAndroidRawId
                                + ")");
                addChangesToCommit(
                        ContentProviderOperation.newInsert(Data.CONTENT_URI)
                                .withValue(Data.RAW_CONTACT_ID, mAndroidRawId)
                                .withValue(
                                        Data.MIMETYPE,
                                        CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                                .withValue(CommonDataKinds.Organization.COMPANY, org)
                                .build());
            }
        }
    }

    void setPhoto(byte[] photo) {
        if (photo == null) {
            Log.e("[Contact] Can't set null picture.");
            return;
        }

        if (mAndroidRawId == null) {
            Log.w("[Contact] Can't set picture for not already created contact, will do it later");
            mTempPicture = photo;
        } else {
            Log.i(
                    "[Contact] Setting picture to an already created raw contact [",
                    mAndroidRawId,
                    "]");
            try {
                long rawId = Long.parseLong(mAndroidRawId);

                Uri rawContactPhotoUri =
                        Uri.withAppendedPath(
                                ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawId),
                                RawContacts.DisplayPhoto.CONTENT_DIRECTORY);

                if (rawContactPhotoUri != null) {
                    ContentResolver resolver =
                            LinphoneContext.instance().getApplicationContext().getContentResolver();
                    AssetFileDescriptor fd =
                            resolver.openAssetFileDescriptor(rawContactPhotoUri, "rw");
                    OutputStream os = fd.createOutputStream();
                    os.write(photo);
                    os.close();
                    fd.close();
                } else {
                    Log.e(
                            "[Contact] Failed to get raw contact photo URI for raw contact id [",
                            rawId,
                            "], aborting");
                }
            } catch (NumberFormatException nfe) {
                Log.e("[Contact] Couldn't parse raw id [", mAndroidId, "], aborting");
            } catch (IOException ioe) {
                Log.e("[Contact] Couldn't set picture, IO error: ", ioe);
            } catch (Exception e) {
                Log.e("[Contact] Couldn't set picture, unknown error: ", e);
            }
        }
    }

    private String findRawContactID() {
        ContentResolver resolver =
                LinphoneContext.instance().getApplicationContext().getContentResolver();
        String result = null;
        String[] projection = {RawContacts._ID};

        String selection = RawContacts.CONTACT_ID + "=?";
        Cursor c =
                resolver.query(
                        RawContacts.CONTENT_URI,
                        projection,
                        selection,
                        new String[] {mAndroidId},
                        null);
        if (c != null) {
            if (c.moveToFirst()) {
                result = c.getString(c.getColumnIndex(RawContacts._ID));
            }
            c.close();
        }
        return result;
    }

    void createRawLinphoneContactFromExistingAndroidContactIfNeeded() {
        if (LinphoneContext.instance()
                .getApplicationContext()
                .getResources()
                .getBoolean(R.bool.use_linphone_tag)) {
            if (mAndroidId != null && (mAndroidRawId == null || !isAndroidRawIdLinphone)) {
                if (mAndroidRawId == null) {
                    Log.d("[Contact] RAW ID not found for contact " + mAndroidId);
                    mAndroidRawId = findRawContactID();
                }
                Log.d("[Contact] Found RAW ID for contact " + mAndroidId + " : " + mAndroidRawId);

                String linphoneRawId = findLinphoneRawContactId();
                if (linphoneRawId == null) {
                    Log.d("[Contact] Linphone RAW ID not found for contact " + mAndroidId);
                    createRawLinphoneContactFromExistingAndroidContact();
                } else {
                    Log.d(
                            "[Contact] Linphone RAW ID found for contact "
                                    + mAndroidId
                                    + " : "
                                    + linphoneRawId);
                    mAndroidRawId = linphoneRawId;
                }
                isAndroidRawIdLinphone = true;
            }
        }
    }

    private void createRawLinphoneContactFromExistingAndroidContact() {
        addChangesToCommit(
                ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                        .withValue(
                                RawContacts.ACCOUNT_TYPE,
                                ContactsManager.getInstance().getString(R.string.sync_account_type))
                        .withValue(
                                RawContacts.ACCOUNT_NAME,
                                ContactsManager.getInstance().getString(R.string.sync_account_name))
                        .withValue(
                                RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_DEFAULT)
                        .build());

        addChangesToCommit(
                ContentProviderOperation.newUpdate(
                                ContactsContract.AggregationExceptions.CONTENT_URI)
                        .withValue(
                                ContactsContract.AggregationExceptions.TYPE,
                                ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER)
                        .withValue(
                                ContactsContract.AggregationExceptions.RAW_CONTACT_ID1,
                                mAndroidRawId)
                        .withValueBackReference(
                                ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, 0)
                        .build());

        Log.i(
                "[Contact] Creating linphone RAW contact for contact "
                        + mAndroidId
                        + " linked with existing RAW contact "
                        + mAndroidRawId);
        saveChangesCommited();
    }

    private String findLinphoneRawContactId() {
        ContentResolver resolver =
                LinphoneContext.instance().getApplicationContext().getContentResolver();
        String result = null;
        String[] projection = {RawContacts._ID};

        String selection = RawContacts.CONTACT_ID + "=? AND " + RawContacts.ACCOUNT_TYPE + "=?";
        Cursor c =
                resolver.query(
                        RawContacts.CONTENT_URI,
                        projection,
                        selection,
                        new String[] {
                            mAndroidId,
                            ContactsManager.getInstance().getString(R.string.sync_account_type)
                        },
                        null);
        if (c != null) {
            if (c.moveToFirst()) {
                result = c.getString(c.getColumnIndex(RawContacts._ID));
            }
            c.close();
        }
        return result;
    }
}
