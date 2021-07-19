package com.fsck.k9.view;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract.Contacts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.loader.app.LoaderManager;
import androidx.loader.app.LoaderManager.LoaderCallbacks;
import androidx.loader.content.Loader;
import android.text.Editable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ListPopupWindow;
import android.widget.ListView;
import android.widget.TextView;

import com.fsck.k9.K9;
import com.fsck.k9.ui.R;
import com.fsck.k9.activity.AlternateRecipientAdapter;
import com.fsck.k9.activity.AlternateRecipientAdapter.AlternateRecipientListener;
import com.fsck.k9.activity.compose.RecipientAdapter;
import com.fsck.k9.activity.compose.RecipientLoader;
import com.fsck.k9.mail.Address;
import com.fsck.k9.ui.compose.RecipientCircleImageView;
import com.fsck.k9.view.RecipientSelectView.Recipient;
import com.tokenautocomplete.TokenCompleteTextView;
import org.apache.james.mime4j.util.CharsetUtil;
import timber.log.Timber;
import de.hdodenhof.circleimageview.CircleImageView;

import static com.fsck.k9.FontSizes.FONT_DEFAULT;


public class RecipientSelectView extends TokenCompleteTextView<Recipient> implements LoaderCallbacks<List<Recipient>>,
        AlternateRecipientListener {

    private static final int MINIMUM_LENGTH_FOR_FILTERING = 2;

    private static final String ARG_QUERY = "query";

    private static final int LOADER_ID_FILTERING = 0;
    private static final int LOADER_ID_ALTERNATES = 1;


    private RecipientAdapter adapter;
    @Nullable
    private String cryptoProvider;
    private boolean showAdvancedInfo;
    private boolean showCryptoEnabled;
    @Nullable
    private LoaderManager loaderManager;

    private ListPopupWindow alternatesPopup;
    private AlternateRecipientAdapter alternatesAdapter;
    private Recipient alternatesPopupRecipient;
    private TokenListener<Recipient> listener;
    private int tokenTextSize = FONT_DEFAULT;


    public RecipientSelectView(Context context) {
        super(context);
        initView(context);
    }

    public RecipientSelectView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public RecipientSelectView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView(context);
    }

    private void initView(Context context) {
        // TODO: validator?

        alternatesPopup = new ListPopupWindow(context);
        alternatesAdapter = new AlternateRecipientAdapter(context, this);
        alternatesPopup.setAdapter(alternatesAdapter);

        // if a token is completed, pick an entry based on best guess.
        // Note that we override performCompletion, so this doesn't actually do anything
        performBestGuess(true);

        adapter = new RecipientAdapter(context);
        setAdapter(adapter);

        setLongClickable(true);
    }

    @Override
    public boolean shouldIgnoreToken(Recipient token) {
        // don't allow duplicates, based on equality of recipient objects, which is email addresses
        return getObjects().contains(token);
    }

    public void setTokenTextSize(int tokenTextSize) {
        this.tokenTextSize = tokenTextSize;
    }

    @Override
    protected View getViewForObject(Recipient recipient) {
        View view = inflateLayout();

        RecipientTokenViewHolder holder = new RecipientTokenViewHolder(view);
        view.setTag(holder);

        bindObjectView(recipient, view);

        return view;
    }

    @SuppressLint("InflateParams")
    private View inflateLayout() {
        LayoutInflater layoutInflater = LayoutInflater.from(getContext());
        View view = layoutInflater.inflate(R.layout.recipient_token_item, null, false);

        // Since the recipient chip views are not part of the view hierarchy we need to manually invalidate this
        // RecipientSelectView whenever a contact picture was loaded in order for the image to be drawn.
        RecipientCircleImageView contactPhotoView = view.findViewById(R.id.contact_photo);
        contactPhotoView.setOnSetImageDrawableListener(this::postInvalidate);

        return view;
    }

    private void bindObjectView(Recipient recipient, View view) {
        RecipientTokenViewHolder holder = (RecipientTokenViewHolder) view.getTag();

        holder.vName.setText(recipient.getDisplayNameOrAddress());
        if (tokenTextSize != FONT_DEFAULT) {
            holder.vName.setTextSize(TypedValue.COMPLEX_UNIT_SP, tokenTextSize);
        }

        RecipientAdapter.setContactPhotoOrPlaceholder(getContext(), holder.vContactPhoto, recipient);

        boolean hasCryptoProvider = cryptoProvider != null;
        if (!hasCryptoProvider) {
            holder.hideCryptoState();
            return;
        }

        boolean isAvailable = recipient.cryptoStatus == RecipientCryptoStatus.AVAILABLE_TRUSTED ||
                recipient.cryptoStatus == RecipientCryptoStatus.AVAILABLE_UNTRUSTED;
        if (!showAdvancedInfo) {
            holder.showSimpleCryptoState(isAvailable, showCryptoEnabled);
        } else {
            boolean isVerified = recipient.cryptoStatus == RecipientCryptoStatus.AVAILABLE_TRUSTED;
            holder.showAdvancedCryptoState(isAvailable, isVerified);
        }
    }

    @Override
    protected Recipient defaultObject(String completionText) {
        Address[] parsedAddresses = Address.parse(completionText);
        if (!CharsetUtil.isASCII(completionText)) {
            setError(getContext().getString(R.string.recipient_error_non_ascii));
            return null;
        }
        if (parsedAddresses.length == 0 || parsedAddresses[0].getAddress() == null) {
            setError(getContext().getString(R.string.recipient_error_parse_failed));
            return null;
        }

        return new Recipient(parsedAddresses[0]);
    }

    public boolean isEmpty() {
        return getObjects().isEmpty();
    }

    public void setLoaderManager(@Nullable LoaderManager loaderManager) {
        this.loaderManager = loaderManager;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (loaderManager != null) {
            loaderManager.destroyLoader(LOADER_ID_ALTERNATES);
            loaderManager.destroyLoader(LOADER_ID_FILTERING);
            loaderManager = null;
        }
    }

    @Override
    public void onFocusChanged(boolean hasFocus, int direction, Rect previous) {
        if (!hasFocus) {
            performCompletion();
        }

        super.onFocusChanged(hasFocus, direction, previous);
        if (hasFocus) {
            displayKeyboard();
        }
    }

    /**
     * TokenCompleteTextView removes composing strings, and etc, but leaves internal composition
     * predictions partially constructed. Changing either/or the Selection or Candidate start/end
     * positions, forces the IMM to reset cleaner.
     */
    @Override
    protected void replaceText(CharSequence text) {
        super.replaceText(text);

        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        imm.updateSelection(this, getSelectionStart(), getSelectionEnd(), -1, -1);
    }

    private void displayKeyboard() {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) {
            return;
        }
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
    }

    @Override
    public void showDropDown() {
        boolean cursorIsValid = adapter != null;
        if (!cursorIsValid) {
            return;
        }

        super.showDropDown();
    }

    @Override
    public void performCompletion() {
        if (getListSelection() == ListView.INVALID_POSITION && enoughToFilter()) {
            Object recipientText = defaultObject(currentCompletionText());
            if (recipientText != null) {
                replaceText(convertSelectionToString(recipientText));
            }
        } else {
            super.performCompletion();
        }
    }

    @Override
    protected void performFiltering(@NonNull CharSequence text, int keyCode) {
        if (loaderManager == null) {
            return;
        }

        String query = currentCompletionText();
        if (TextUtils.isEmpty(query) || query.length() < MINIMUM_LENGTH_FOR_FILTERING) {
            loaderManager.destroyLoader(LOADER_ID_FILTERING);
            return;
        }

        Bundle args = new Bundle();
        args.putString(ARG_QUERY, query);
        loaderManager.restartLoader(LOADER_ID_FILTERING, args, this);
    }

    public void setCryptoProvider(@Nullable String cryptoProvider, boolean showAdvancedInfo) {
        this.cryptoProvider = cryptoProvider;
        this.showAdvancedInfo = showAdvancedInfo;
        adapter.setShowAdvancedInfo(showAdvancedInfo);
        alternatesAdapter.setShowAdvancedInfo(showAdvancedInfo);
    }

    public void setShowCryptoEnabled(boolean showCryptoEnabled) {
        this.showCryptoEnabled = showCryptoEnabled;

        redrawAllTokens();
    }

    private void redrawAllTokens() {
        Editable text = getText();
        if (text == null) {
            return;
        }

        RecipientTokenSpan[] recipientSpans = text.getSpans(0, text.length(), RecipientTokenSpan.class);
        for (RecipientTokenSpan recipientSpan : recipientSpans) {
            bindObjectView(recipientSpan.getToken(), recipientSpan.view);
        }

        invalidate();
        invalidateCursorPositionHack();
    }

    public void addRecipients(Recipient... recipients) {
        for (Recipient recipient : recipients) {
            addObjectSync(recipient);
        }
    }

    public Address[] getAddresses() {
        List<Recipient> recipients = getObjects();
        Address[] address = new Address[recipients.size()];
        for (int i = 0; i < address.length; i++) {
            address[i] = recipients.get(i).address;
        }

        return address;
    }

    private void showAlternates(Recipient recipient) {
        if (loaderManager == null) {
            return;
        }

        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(getWindowToken(), 0);

        alternatesPopupRecipient = recipient;
        loaderManager.restartLoader(LOADER_ID_ALTERNATES, null, RecipientSelectView.this);
    }

    public void postShowAlternatesPopup(final List<Recipient> data) {
        // We delay this call so the soft keyboard is gone by the time the popup is layouted
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                showAlternatesPopup(data);
            }
        });
    }

    public void showAlternatesPopup(List<Recipient> data) {
        if (loaderManager == null) {
            return;
        }

        // Copy anchor settings from the autocomplete dropdown
        View anchorView = getRootView().findViewById(getDropDownAnchor());
        alternatesPopup.setAnchorView(anchorView);
        alternatesPopup.setWidth(getDropDownWidth());

        alternatesAdapter.setCurrentRecipient(alternatesPopupRecipient);
        alternatesAdapter.setAlternateRecipientInfo(data);

        // Clear the checked item.
        alternatesPopup.show();
        ListView listView = alternatesPopup.getListView();
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && alternatesPopup.isShowing()) {
            alternatesPopup.dismiss();
            return true;
        }
        return super.onKeyPreIme(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        alternatesPopup.dismiss();
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public Loader<List<Recipient>> onCreateLoader(int id, Bundle args) {
        switch (id) {
            case LOADER_ID_FILTERING: {
                String query = args != null && args.containsKey(ARG_QUERY) ? args.getString(ARG_QUERY) : "";
                adapter.setHighlight(query);
                return new RecipientLoader(getContext(), cryptoProvider, query);
            }
            case LOADER_ID_ALTERNATES: {
                Uri contactLookupUri = alternatesPopupRecipient.getContactLookupUri();
                if (contactLookupUri != null) {
                    return new RecipientLoader(getContext(), cryptoProvider, contactLookupUri, true);
                } else {
                    return new RecipientLoader(getContext(), cryptoProvider, alternatesPopupRecipient.address);
                }
            }
        }

        throw new IllegalStateException("Unknown Loader ID: " + id);
    }

    @Override
    public void onLoadFinished(Loader<List<Recipient>> loader, List<Recipient> data) {
        if (loaderManager == null) {
            return;
        }

        switch (loader.getId()) {
            case LOADER_ID_FILTERING: {
                adapter.setRecipients(data);
                break;
            }
            case LOADER_ID_ALTERNATES: {
                postShowAlternatesPopup(data);
                loaderManager.destroyLoader(LOADER_ID_ALTERNATES);
                break;
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<List<Recipient>> loader) {
        if (loader.getId() == LOADER_ID_FILTERING) {
            adapter.setHighlight(null);
            adapter.setRecipients(null);
        }
    }

    public boolean tryPerformCompletion() {
        if (!hasUncompletedText()) {
            return false;
        }
        int previousNumRecipients = getTokenCount();
        performCompletion();
        int numRecipients = getTokenCount();

        return previousNumRecipients != numRecipients;
    }

    private int getTokenCount() {
        return getObjects().size();
    }

    public boolean hasUncompletedText() {
        String currentCompletionText = currentCompletionText();
        return !TextUtils.isEmpty(currentCompletionText) && !isPlaceholderText(currentCompletionText);
    }

    static private boolean isPlaceholderText(String currentCompletionText) {
        // TODO string matching here is sort of a hack, but it's somewhat reliable and the info isn't easily available
        return currentCompletionText.startsWith("+") && currentCompletionText.substring(1).matches("[0-9]+");
    }

    @Override
    public void onRecipientRemove(Recipient currentRecipient) {
        alternatesPopup.dismiss();
        removeObjectSync(currentRecipient);
    }

    @Override
    public void onRecipientChange(Recipient recipientToReplace, Recipient alternateAddress) {
        alternatesPopup.dismiss();

        List<Recipient> currentRecipients = getObjects();
        int indexOfRecipient = currentRecipients.indexOf(recipientToReplace);
        if (indexOfRecipient == -1) {
            Timber.e("Tried to refresh invalid view token!");
            return;
        }
        Recipient currentRecipient = currentRecipients.get(indexOfRecipient);

        currentRecipient.address = alternateAddress.address;
        currentRecipient.addressLabel = alternateAddress.addressLabel;
        currentRecipient.cryptoStatus = alternateAddress.cryptoStatus;

        View recipientTokenView = getTokenViewForRecipient(currentRecipient);
        if (recipientTokenView == null) {
            Timber.e("Tried to refresh invalid view token!");
            return;
        }

        bindObjectView(currentRecipient, recipientTokenView);

        if (listener != null) {
            listener.onTokenChanged(currentRecipient);
        }

        invalidate();
        invalidateCursorPositionHack();
    }

    /**
     * Changing the size of our RecipientTokenSpan doesn't seem to redraw the cursor in the new position. This will
     * make sure the cursor position is recalculated.
     */
    private void invalidateCursorPositionHack() {
        int oldStart = getSelectionStart();
        int oldEnd = getSelectionEnd();

        // The selection values need to actually change in order for the cursor to be redrawn. If the cursor already
        // is at position 0 this won't trigger a redraw. But that's fine because the size of our span can't influence
        // cursor position 0.
        setSelection(0);

        setSelection(oldStart, oldEnd);
    }

    /**
     * This method builds the span given a recipient object. We override it with identical
     * functionality, but using the custom RecipientTokenSpan class which allows us to
     * retrieve the view for redrawing at a later point.
     */
    @Override
    protected TokenImageSpan buildSpanForObject(Recipient obj) {
        if (obj == null) {
            return null;
        }

        View tokenView = getViewForObject(obj);
        return new RecipientTokenSpan(tokenView, obj);
    }

    /**
     * Find the token view tied to a given recipient. This method relies on spans to
     * be of the RecipientTokenSpan class, as created by the buildSpanForObject method.
     */
    private View getTokenViewForRecipient(Recipient currentRecipient) {
        Editable text = getText();
        if (text == null) {
            return null;
        }

        RecipientTokenSpan[] recipientSpans = text.getSpans(0, text.length(), RecipientTokenSpan.class);
        for (RecipientTokenSpan recipientSpan : recipientSpans) {
            if (recipientSpan.getToken().equals(currentRecipient)) {
                return recipientSpan.view;
            }
        }

        return null;
    }

    /**
     * We use a specialized version of TokenCompleteTextView.TokenListener as well,
     * adding a callback for onTokenChanged.
     */
    public void setTokenListener(TokenListener<Recipient> listener) {
        super.setTokenListener(listener);
        this.listener = listener;
    }


    public enum RecipientCryptoStatus {
        UNDEFINED,
        UNAVAILABLE,
        AVAILABLE_UNTRUSTED,
        AVAILABLE_TRUSTED
    }

    public interface TokenListener<T> extends TokenCompleteTextView.TokenListener<T> {
        void onTokenChanged(T token);
    }

    private class RecipientTokenSpan extends TokenImageSpan {
        private final View view;
        private boolean initialLayoutPerformed = false;

        public RecipientTokenSpan(View view, Recipient recipient) {
            super(view, recipient);
            this.view = view;
        }

        @Override
        public void onClick() {
            showAlternates(getToken());
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence charSequence, int start, int end,
                @Nullable Paint.FontMetricsInt fontMetricsInt) {
            if (initialLayoutPerformed && view.isLayoutRequested()) {
                relayoutView();
            }

            int size = super.getSize(paint, charSequence, start, end, fontMetricsInt);
            initialLayoutPerformed = true;
            return size;
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y,
                int bottom, @NonNull Paint paint) {
            if (initialLayoutPerformed && view.isLayoutRequested()) {
                relayoutView();
            }

            super.draw(canvas, text, start, end, x, top, y, bottom, paint);

            // Dispatch onPreDraw event so image loading using Glide will work properly.
            view.findViewById(R.id.contact_photo).getViewTreeObserver().dispatchOnPreDraw();

            initialLayoutPerformed = true;
        }

        // Hack to support layout changes
        // TODO: Remove once a TokenAutoComplete release includes https://github.com/splitwise/TokenAutoComplete/pull/403
        private void relayoutView() {
            int maxViewSpanWidth = getMaxViewSpanWidth();

            int spec = View.MeasureSpec.AT_MOST;
            if (maxViewSpanWidth == 0) {
                //If the width is 0, allow the view to choose it's own content size
                spec = View.MeasureSpec.UNSPECIFIED;
            }
            int widthSpec = View.MeasureSpec.makeMeasureSpec(maxViewSpanWidth, spec);
            int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);

            view.measure(widthSpec, heightSpec);
            view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        }
    }

    private static class RecipientTokenViewHolder {
        final TextView vName;
        final CircleImageView vContactPhoto;
        final View cryptoStatusRed;
        final View cryptoStatusOrange;
        final View cryptoStatusGreen;
        final View cryptoStatusSimple;
        final View cryptoStatusSimpleEnabled;
        final View cryptoStatusSimpleError;


        RecipientTokenViewHolder(View view) {
            vName = view.findViewById(android.R.id.text1);
            vContactPhoto = view.findViewById(R.id.contact_photo);
            cryptoStatusRed = view.findViewById(R.id.contact_crypto_status_red);
            cryptoStatusOrange = view.findViewById(R.id.contact_crypto_status_orange);
            cryptoStatusGreen = view.findViewById(R.id.contact_crypto_status_green);

            cryptoStatusSimple = view.findViewById(R.id.contact_crypto_status_icon_simple);
            cryptoStatusSimpleEnabled = view.findViewById(R.id.contact_crypto_status_icon_simple_enabled);
            cryptoStatusSimpleError = view.findViewById(R.id.contact_crypto_status_icon_simple_error);
        }

        void showSimpleCryptoState(boolean isAvailable, boolean isShowEnabled) {
            cryptoStatusRed.setVisibility(View.GONE);
            cryptoStatusOrange.setVisibility(View.GONE);
            cryptoStatusGreen.setVisibility(View.GONE);

            cryptoStatusSimple.setVisibility(!isShowEnabled && isAvailable ? View.VISIBLE : View.GONE);
            cryptoStatusSimpleEnabled.setVisibility(isShowEnabled && isAvailable ? View.VISIBLE : View.GONE);
            cryptoStatusSimpleError.setVisibility(isShowEnabled && !isAvailable ? View.VISIBLE : View.GONE);
        }

        void showAdvancedCryptoState(boolean isAvailable, boolean isVerified) {
            cryptoStatusRed.setVisibility(!isAvailable ? View.VISIBLE : View.GONE);
            cryptoStatusOrange.setVisibility(isAvailable && !isVerified ? View.VISIBLE : View.GONE);
            cryptoStatusGreen.setVisibility(isAvailable && isVerified ? View.VISIBLE : View.GONE);

            cryptoStatusSimple.setVisibility(View.GONE);
            cryptoStatusSimpleEnabled.setVisibility(View.GONE);
            cryptoStatusSimpleError.setVisibility(View.GONE);
        }

        void hideCryptoState() {
            cryptoStatusRed.setVisibility(View.GONE);
            cryptoStatusOrange.setVisibility(View.GONE);
            cryptoStatusGreen.setVisibility(View.GONE);

            cryptoStatusSimple.setVisibility(View.GONE);
            cryptoStatusSimpleEnabled.setVisibility(View.GONE);
            cryptoStatusSimpleError.setVisibility(View.GONE);
        }
    }

    public static class Recipient implements Serializable {
        @Nullable // null means the address is not associated with a contact
        public final Long contactId;
        public final String contactLookupKey;

        @NonNull
        public Address address;

        public String addressLabel;
        public final int timesContacted;
        public final String sortKey;
        public final boolean starred;

        @Nullable // null if the contact has no photo. transient because we serialize this manually, see below.
        public transient Uri photoThumbnailUri;

        @NonNull
        private RecipientCryptoStatus cryptoStatus;

        public Recipient(@NonNull Address address) {
            this.address = address;
            this.contactId = null;
            this.cryptoStatus = RecipientCryptoStatus.UNDEFINED;
            this.contactLookupKey = null;
            timesContacted = 0;
            sortKey = null;
            starred = false;
        }

        public Recipient(String name, String email, String addressLabel, long contactId, String lookupKey,
                int timesContacted, String sortKey, boolean starred) {
            this.address = new Address(email, name);
            this.contactId = contactId;
            this.addressLabel = addressLabel;
            this.cryptoStatus = RecipientCryptoStatus.UNDEFINED;
            this.contactLookupKey = lookupKey;
            this.timesContacted = timesContacted;
            this.sortKey = sortKey;
            this.starred = starred;
        }

        public String getDisplayNameOrAddress() {
            final String displayName = K9.isShowCorrespondentNames() ? getDisplayName() : null;

            if (displayName != null) {
                return displayName;
            }

            return address.getAddress();
        }

        public boolean isValidEmailAddress() {
            return (address.getAddress() != null);
        }

        public String getDisplayNameOrUnknown(Context context) {
            String displayName = getDisplayName();
            if (displayName != null) {
                return displayName;
            }

            return context.getString(R.string.unknown_recipient);
        }

        public String getNameOrUnknown(Context context) {
            String name = address.getPersonal();
            if (name != null) {
                return name;
            }

            return context.getString(R.string.unknown_recipient);
        }

        private String getDisplayName() {
            if (TextUtils.isEmpty(address.getPersonal())) {
                return null;
            }

            return address.getPersonal();
        }

        @NonNull
        public RecipientCryptoStatus getCryptoStatus() {
            return cryptoStatus;
        }

        public void setCryptoStatus(@NonNull RecipientCryptoStatus cryptoStatus) {
            this.cryptoStatus = cryptoStatus;
        }

        @Nullable
        public Uri getContactLookupUri() {
            if (contactId == null) {
                return null;
            }

            return Contacts.getLookupUri(contactId, contactLookupKey);
        }

        @Override
        public boolean equals(Object o) {
            // Equality is entirely up to the address
            return o instanceof Recipient && address.equals(((Recipient) o).address);
        }

        @NonNull
        @Override
        public String toString() {
            return address.toString();
        }

        private void writeObject(ObjectOutputStream oos) throws IOException {
            oos.defaultWriteObject();

            // custom serialization, Android's Uri class is not serializable
            if (photoThumbnailUri != null) {
                oos.writeInt(1);
                oos.writeUTF(photoThumbnailUri.toString());
            } else {
                oos.writeInt(0);
            }
        }

        private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
            ois.defaultReadObject();

            // custom deserialization, Android's Uri class is not serializable
            if (ois.readInt() != 0) {
                String uriString = ois.readUTF();
                photoThumbnailUri = Uri.parse(uriString);
            }
        }
    }
}
