package com.stickersanimated.kissing.ui;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import es.dmoral.toasty.Toasty;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

// import com.facebook.CallbackManager;
// import com.facebook.FacebookCallback;
// import com.facebook.FacebookException;
// import com.facebook.GraphRequest;
// import com.facebook.GraphResponse;
// import com.facebook.login.LoginManager;
// import com.facebook.login.LoginResult;
// import com.facebook.login.widget.LoginButton;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.messaging.FirebaseMessaging;
import com.hbb20.CountryCodePicker;
import com.stickersanimated.kissing.Manager.PrefManager;
import com.stickersanimated.kissing.R;
import com.stickersanimated.kissing.api.apiClient;
import com.stickersanimated.kissing.api.apiRest;
import com.stickersanimated.kissing.entity.ApiResponse;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    private SignInButton sign_in_button_google;
    private GoogleSignInClient mGoogleSignInClient;

    private ProgressDialog register_progress;
    private RelativeLayout relative_layout_google_login;
    private RelativeLayout relative_layout_phone_login;
    private CountryCodePicker countryCodePicker;
    private RelativeLayout relative_layout_confirm_phone_number;
    private EditText otp_edit_text_login_activity;
    private RelativeLayout relative_layout_confirm_top_login_activity;
    private EditText edit_text_phone_number_login_acitivty;
    private LinearLayout linear_layout_buttons_login_activity;
    private LinearLayout linear_layout_otp_confirm_login_activity;
    private LinearLayout linear_layout_phone_input_login_activity;
    private RelativeLayout relative_layout_confirm_full_name;
    private LinearLayout linear_layout_name_input_login_activity;
    private EditText edit_text_name_login_acitivty;
    private String phoneNum ="";
    private String token ="";
    private CheckBox check_box_login_activity_privacy;

    private PrefManager prf;
    private TextView text_view_login_activity_privacy;

    private FirebaseAuth mAuth;
    private String verificationId;

    // Email and password accounts, shown only when the panel switches them on.
    private LinearLayout linear_layout_manual_account;
    private EditText edit_text_manual_name;
    private EditText edit_text_manual_email;
    private EditText edit_text_manual_password;
    private TextView text_view_manual_submit;
    private TextView text_view_manual_toggle;
    /** False while the form signs an existing account in, true while it creates one. */
    private boolean registering = false;
    private static final String DEFAULT_AVATAR =
            "https://lh3.googleusercontent.com/-XdUIqdMkCWA/AAAAAAAAAAI/AAAAAAAAAAA/4252rscbv5M/photo.jpg";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        prf= new PrefManager(getApplicationContext());

        if (prf.getString("LOGGED").toString().equals("TRUE")){
            Intent intent= new Intent(LoginActivity.this,HomeActivity.class);
            startActivity(intent);
            finish(); // Finish LoginActivity so user can't go back to it
        }

        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                            return;
                        }
                        LoginActivity.this.token = task.getResult();
                    }
                });

        initView();
        initAction();
        mAuth = FirebaseAuth.getInstance();
    }

    public void initView(){
        this.edit_text_name_login_acitivty   =      (EditText)  findViewById(R.id.edit_text_name_login_acitivty);
        this.edit_text_phone_number_login_acitivty   =      (EditText)  findViewById(R.id.edit_text_phone_number_login_acitivty);
        this.otp_edit_text_login_activity   =      (EditText)  findViewById(R.id.otp_edit_text_login_activity);
        this.relative_layout_confirm_top_login_activity   =      (RelativeLayout)  findViewById(R.id.relative_layout_confirm_top_login_activity);
        this.relative_layout_google_login   =      (RelativeLayout)  findViewById(R.id.relative_layout_google_login);
        this.sign_in_button_google   =      (SignInButton)  findViewById(R.id.sign_in_button_google);
        this.relative_layout_phone_login =      (RelativeLayout)   findViewById(R.id.relative_layout_phone_login);
        this.relative_layout_confirm_phone_number =      (RelativeLayout)   findViewById(R.id.relative_layout_confirm_phone_number);
        this.linear_layout_buttons_login_activity =      (LinearLayout)   findViewById(R.id.linear_layout_buttons_login_activity);
        this.linear_layout_otp_confirm_login_activity =      (LinearLayout)   findViewById(R.id.linear_layout_otp_confirm_login_activity);
        this.linear_layout_phone_input_login_activity =      (LinearLayout)   findViewById(R.id.linear_layout_phone_input_login_activity);
        this.linear_layout_name_input_login_activity =      (LinearLayout)   findViewById(R.id.linear_layout_name_input_login_activity);
        this.relative_layout_confirm_full_name =      (RelativeLayout)   findViewById(R.id.relative_layout_confirm_full_name);
        this.countryCodePicker =      (CountryCodePicker)   findViewById(R.id.CountryCodePicker);
        this.check_box_login_activity_privacy = (CheckBox) findViewById(R.id.check_box_login_activity_privacy);
        this.text_view_login_activity_privacy = (TextView) findViewById(R.id.text_view_login_activity_privacy);
        this.linear_layout_manual_account = (LinearLayout) findViewById(R.id.linear_layout_manual_account);
        this.edit_text_manual_name = (EditText) findViewById(R.id.edit_text_manual_name);
        this.edit_text_manual_email = (EditText) findViewById(R.id.edit_text_manual_email);
        this.edit_text_manual_password = (EditText) findViewById(R.id.edit_text_manual_password);
        this.text_view_manual_submit = (TextView) findViewById(R.id.text_view_manual_submit);
        this.text_view_manual_toggle = (TextView) findViewById(R.id.text_view_manual_toggle);
    }
    public void initAction(){
        initManualAccount();

        this.text_view_login_activity_privacy.setOnClickListener(view -> {
            startActivity(new Intent(LoginActivity.this,PolicyActivity.class));
        });
        this.check_box_login_activity_privacy.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked){
                    check_box_login_activity_privacy.setError(null);
                    relative_layout_phone_login.setAlpha(1);
                    relative_layout_google_login.setAlpha(1);
                }else{
                    relative_layout_phone_login.setAlpha((float) 0.7);
                    relative_layout_google_login.setAlpha((float) 0.7);
                }
            }
        });
        relative_layout_confirm_full_name.setOnClickListener(v->{

            String token_user =  prf.getString("TOKEN_USER");
            String id_user =  prf.getString("ID_USER");
            if (edit_text_name_login_acitivty.getText().toString().length()<3) {
                Toasty.error(getApplicationContext(), "This name very shot ", Toast.LENGTH_LONG).show();
                return;
            }
            updateToken(Integer.parseInt(id_user),token_user,token,edit_text_name_login_acitivty.getText().toString());
        });
        relative_layout_confirm_top_login_activity.setOnClickListener(v->{
            if(otp_edit_text_login_activity.getText().toString().length()<6){
                Toasty.error(this, "The verification code you have been entered incorrect !", Toast.LENGTH_SHORT).show();
            }else{
                verifyCode(otp_edit_text_login_activity.getText().toString());
            }
        });

        this.relative_layout_phone_login.setOnClickListener(v -> {
            if (!check_box_login_activity_privacy.isChecked()){
                check_box_login_activity_privacy.setError(getResources().getString(R.string.accept_privacy_policy_error));
                return;
            }
            linear_layout_buttons_login_activity.setVisibility(View.GONE);
            linear_layout_phone_input_login_activity.setVisibility(View.VISIBLE);
        });
        relative_layout_confirm_phone_number.setOnClickListener(v ->{
            phoneNum = "+"+countryCodePicker.getSelectedCountryCode().toString()+edit_text_phone_number_login_acitivty.getText().toString();

            new AlertDialog.Builder(this)
                    .setTitle("We will be verifying the phone number:"  )
                    .setMessage(" \n"+phoneNum+" \n\n Is this OK,or would you like to edit the number ?")
                    .setPositiveButton("Confrim",
                            (dialog, which) -> {
                                loginWithPhone();
                            })
                    .setNegativeButton("Edit",
                            (dialog, which) -> {
                                dialog.dismiss();
                            }).show();
        });
        View.OnClickListener googleSignInClickListener = view -> {
            if (!check_box_login_activity_privacy.isChecked()){
                check_box_login_activity_privacy.setError(getResources().getString(R.string.accept_privacy_policy_error));
                return;
            }
            signIn();
        };
        relative_layout_google_login.setOnClickListener(googleSignInClickListener);
        this.sign_in_button_google.setOnClickListener(googleSignInClickListener);
    }

    private void loginWithPhone() {
        linear_layout_phone_input_login_activity.setVisibility(View.GONE);
        linear_layout_otp_confirm_login_activity.setVisibility(View.VISIBLE);
        sendVerificationCode(phoneNum);
    }

    private void sendVerificationCode(String number) {
        PhoneAuthOptions options =
                PhoneAuthOptions.newBuilder(mAuth)
                        .setPhoneNumber(number)
                        .setTimeout(60L, TimeUnit.SECONDS)
                        .setActivity(this)
                        .setCallbacks(mCallBack)
                        .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private final PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallBack = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        @Override
        public void onCodeSent(@NonNull String s, @NonNull PhoneAuthProvider.ForceResendingToken forceResendingToken) {
            super.onCodeSent(s, forceResendingToken);
            verificationId = s;
            Log.v("PHONE",s);
        }

        @Override
        public void onVerificationCompleted(@NonNull PhoneAuthCredential phoneAuthCredential) {
            final String code = phoneAuthCredential.getSmsCode();
            Log.v("PHONE code ", (code != null) ? code : "is null");
            if (code != null) {
                otp_edit_text_login_activity.setText(code);
                verifyCode(code);
            }
        }

        @Override
        public void onVerificationFailed(@NonNull FirebaseException e) {
            Toast.makeText(LoginActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    };
    private void verifyCode(String code) {
        // Firebase throws rather than returning an error when the verification id is
        // missing, which is what happens if the code never arrived - no Play Services,
        // a failed send, or Confirm tapped before the SMS landed.
        if (verificationId == null || verificationId.isEmpty()) {
            Toasty.error(this, getString(R.string.login_no_code_sent), Toast.LENGTH_LONG).show();
            return;
        }
        if (code == null || code.trim().isEmpty()) {
            Toasty.error(this, getString(R.string.login_enter_code), Toast.LENGTH_SHORT).show();
            return;
        }
        final PhoneAuthCredential credential =
                PhoneAuthProvider.getCredential(verificationId, code.trim());
        signInWithCredential(credential);
    }
    private void signInWithCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            String photo = "https://lh3.googleusercontent.com/-XdUIqdMkCWA/AAAAAAAAAAI/AAAAAAAAAAA/4252rscbv5M/photo.jpg" ;
                            signUp(phoneNum,phoneNum,"null","phone",photo);
                        } else {
                            Toast.makeText(LoginActivity.this, task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void signIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        GoogleSignInClient mGoogleSignInClient = GoogleSignIn.getClient(LoginActivity.this, gso);

        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        mGoogleSignInLauncher.launch(signInIntent);
    }

    ActivityResultLauncher<Intent> mGoogleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                final Task<GoogleSignInAccount> task =
                        GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                try {
                    final GoogleSignInAccount account = task.getResult(ApiException.class);
                    if (account == null || account.getIdToken() == null) {
                        Toasty.error(getApplicationContext(),
                                "Google did not return an account", Toast.LENGTH_LONG).show();
                        return;
                    }
                    firebaseAuthWithGoogle(account.getIdToken());
                } catch (ApiException e) {
                    // Sign in used to fail in silence here, which made it look like the
                    // button did nothing. The status code says what actually happened:
                    // 10 is DEVELOPER_ERROR - this build's signing key is not registered
                    // in the Firebase project - 12501 is the user backing out, 7 network.
                    Log.w(TAG, "Google sign in failed, status " + e.getStatusCode(), e);
                    Toasty.error(getApplicationContext(),
                            "Google sign in failed (code " + e.getStatusCode() + ")",
                            Toast.LENGTH_LONG).show();
                }
            });

    private void getResultGoogle(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount acct = completedTask.getResult(ApiException.class);
            if (acct != null) {
                Log.d(TAG, "handleSignInResult:success");
                String photo = "https://lh3.googleusercontent.com/-XdUIqdMkCWA/AAAAAAAAAAI/AAAAAAAAAAA/4252rscbv5M/photo.jpg" ;
                if (acct.getPhotoUrl()!=null){
                    photo =  acct.getPhotoUrl().toString();
                }
                signUp(acct.getId(), acct.getId(), acct.getDisplayName(), "google", photo);
                mGoogleSignInClient.signOut();
            }
        } catch (ApiException e) {
            Log.w(TAG, "signInResult:failed code=" + e.getStatusCode());
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "handleSignInResult:success");
                            String photo = "https://lh3.googleusercontent.com/-XdUIqdMkCWA/AAAAAAAAAAI/AAAAAAAAAAA/4252rscbv5M/photo.jpg" ;
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user.getPhotoUrl()!=null){
                                photo =  user.getPhotoUrl().toString();
                            }
                            signUp(user.getUid(), user.getUid(), user.getDisplayName(), "google", photo);
                            //mGoogleSignInClient.signOut();
                        } else {
                            Toast.makeText(LoginActivity.this, "Failed to Sign IN", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void getResultFacebook(JSONObject object){
        Log.d(TAG, object.toString());
        try {
            signUp(object.getString("id"), object.getString("id"), object.getString("name"), "facebook", object.getJSONObject("picture").getJSONObject("data").getString("url"));
            // LoginManager.getInstance().logOut();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPause(){
        super.onPause();
    }

    /**
     * Email and password sign in and sign up. The whole block stays hidden unless
     * ADMIN_MANUAL_LOGIN is on in the panel, so turning it off in the panel takes it off
     * the login screen without shipping a new build.
     */
    private void initManualAccount() {
        if (!"TRUE".equalsIgnoreCase(prf.getString("ADMIN_MANUAL_LOGIN"))) {
            return;
        }
        linear_layout_manual_account.setVisibility(View.VISIBLE);
        applyManualMode();

        text_view_manual_toggle.setOnClickListener(v -> {
            registering = !registering;
            applyManualMode();
        });
        text_view_manual_submit.setOnClickListener(v -> submitManualAccount());
    }

    /** Swaps the form between signing in and creating an account. */
    private void applyManualMode() {
        edit_text_manual_name.setVisibility(registering ? View.VISIBLE : View.GONE);
        text_view_manual_submit.setText(registering
                ? R.string.login_create_account : R.string.login_sign_in);
        text_view_manual_toggle.setText(registering
                ? R.string.login_go_to_sign_in : R.string.login_go_to_register);
    }

    private void submitManualAccount() {
        if (!check_box_login_activity_privacy.isChecked()) {
            check_box_login_activity_privacy.setError(
                    getResources().getString(R.string.accept_privacy_policy_error));
            return;
        }
        final String email = edit_text_manual_email.getText().toString().trim();
        final String password = edit_text_manual_password.getText().toString();
        final String name = edit_text_manual_name.getText().toString().trim();

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            edit_text_manual_email.setError(getString(R.string.login_need_email));
            edit_text_manual_email.requestFocus();
            return;
        }
        if (password.length() < 6) {
            edit_text_manual_password.setError(getString(R.string.login_need_password));
            edit_text_manual_password.requestFocus();
            return;
        }
        if (registering) {
            if (name.length() < 3) {
                edit_text_manual_name.setError(getString(R.string.login_need_name));
                edit_text_manual_name.requestFocus();
                return;
            }
            // Same call every other provider ends at; the type marks it as an email account.
            signUp(email, password, name, "email", DEFAULT_AVATAR);
            return;
        }
        signInWithEmail(email, password);
    }

    private void signInWithEmail(String email, String password) {
        register_progress = ProgressDialog.show(this, null,
                getResources().getString(R.string.operation_progress), true);
        apiClient.getClient().create(apiRest.class).login(email, password)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(Call<ApiResponse> call, Response<ApiResponse> response) {
                        dismissProgress();
                        final ApiResponse body = response.body();
                        if (body == null) {
                            // Say which failure it was: a server that answered with
                            // something other than the expected JSON is a different
                            // problem from a wrong password, and "operation failed"
                            // hides that.
                            Log.w(TAG, "Sign in got HTTP " + response.code()
                                    + " with no usable body");
                            Toasty.error(getApplicationContext(),
                                    "Sign in failed (server said " + response.code() + ")",
                                    Toast.LENGTH_LONG, true).show();
                            return;
                        }
                        if (!Integer.valueOf(200).equals(body.getCode())) {
                            Toasty.error(getApplicationContext(), body.getMessage(),
                                    Toast.LENGTH_SHORT, true).show();
                            return;
                        }
                        storeAccount(body);
                    }

                    @Override
                    public void onFailure(Call<ApiResponse> call, Throwable t) {
                        dismissProgress();
                        Log.w(TAG, "Sign in could not reach the server", t);
                        Toasty.error(getApplicationContext(),
                                "Could not reach the server, check your connection",
                                Toast.LENGTH_LONG, true).show();
                    }
                });
    }

    /** An account made with an email address has no picture; fall back to the default. */
    private static String avatarOr(String url) {
        return url == null || url.trim().isEmpty() ? DEFAULT_AVATAR : url.trim();
    }

    /** Saves a signed in account and hands the device token to the server, as signUp does. */
    private void storeAccount(ApiResponse body) {
        if (body.getValues() == null || body.getValues().isEmpty()) {
            Toasty.error(getApplicationContext(), "Operation has been cancelled!",
                    Toast.LENGTH_SHORT, true).show();
            return;
        }
        String id_user = "0", name_user = "x", salt_user = "0", token_user = "0";
        for (int i = 0; i < body.getValues().size(); i++) {
            final String field = body.getValues().get(i).getName();
            final String value = body.getValues().get(i).getValue();
            switch (field) {
                case "salt": salt_user = value; break;
                case "token": token_user = value; break;
                case "id": id_user = value; break;
                case "name": name_user = value; break;
                case "type": prf.setString("TYPE_USER", value); break;
                case "username": prf.setString("USERN_USER", value); break;
                case "url": prf.setString("IMAGE_USER", avatarOr(value)); break;
            }
        }
        prf.setString("ID_USER", id_user);
        prf.setString("SALT_USER", salt_user);
        prf.setString("TOKEN_USER", token_user);
        prf.setString("NAME_USER", name_user);
        prf.setString("LOGGED", "TRUE");
        updateToken(Integer.parseInt(id_user), token_user, token, name_user);
    }

    public void signUp(String username,String password,String name,String type,String image){
        register_progress= ProgressDialog.show(this, null,getResources().getString(R.string.operation_progress), true);
        Retrofit retrofit = apiClient.getClient();
        apiRest service = retrofit.create(apiRest.class);
        Call<ApiResponse> call = service.register(name,username,password,type,image);
        call.enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(Call<ApiResponse> call, Response<ApiResponse> response) {
                // Close this dialog first: updateToken below opens one of its own, and
                // whichever field is dismissed afterwards, the other one is left on
                // screen for good.
                dismissProgress();
                if(response.body()!=null){
                    if (response.body().getCode()==200){
                        String id_user="0", name_user="x", salt_user="0", token_user="0";
                        for (int i=0;i<response.body().getValues().size();i++){
                            switch (response.body().getValues().get(i).getName()) {
                                case "salt": salt_user = response.body().getValues().get(i).getValue(); break;
                                case "token": token_user = response.body().getValues().get(i).getValue(); break;
                                case "id": id_user = response.body().getValues().get(i).getValue(); break;
                                case "name": name_user = response.body().getValues().get(i).getValue(); break;
                                case "type": prf.setString("TYPE_USER", response.body().getValues().get(i).getValue()); break;
                                case "username": prf.setString("USERN_USER", response.body().getValues().get(i).getValue()); break;
                                case "url": prf.setString("IMAGE_USER", avatarOr(
                                        response.body().getValues().get(i).getValue())); break;
                                case "enabled":
                                    if (!Boolean.parseBoolean(response.body().getValues().get(i).getValue())) {
                                        Toasty.error(getApplicationContext(), getResources().getString(R.string.account_disabled), Toast.LENGTH_SHORT, true).show();
                                        return;
                                    }
                                    break;
                            }
                        }
                        prf.setString("ID_USER",id_user);
                        prf.setString("SALT_USER",salt_user);
                        prf.setString("TOKEN_USER",token_user);
                        prf.setString("NAME_USER",name_user);
                        prf.setString("LOGGED","TRUE");

                        if ("null".equalsIgnoreCase(name_user)){
                            linear_layout_otp_confirm_login_activity.setVisibility(View.GONE);
                            linear_layout_name_input_login_activity.setVisibility(View.VISIBLE);
                        }else{
                            updateToken(Integer.parseInt(id_user),token_user,token,name_user);
                        }
                    } else {
                        Toasty.error(getApplicationContext(), response.body().getMessage(), Toast.LENGTH_SHORT, true).show();
                    }
                }else{
                    Toasty.error(getApplicationContext(), "Operation has been cancelled!", Toast.LENGTH_SHORT, true).show();
                }
            }
            @Override
            public void onFailure(Call<ApiResponse> call, Throwable t) {
                dismissProgress();
                Toasty.error(getApplicationContext(), "Operation has been cancelled!", Toast.LENGTH_SHORT, true).show();
            }
        });
    }

    public static void set(Activity activity, String s){
        Toasty.error(activity,s,Toast.LENGTH_LONG).show();
        activity.finish();
    }
    public void updateToken(Integer id,String key,String token,String name){
        register_progress= ProgressDialog.show(this, null,getResources().getString(R.string.operation_progress), true);
        Retrofit retrofit = apiClient.getClient();
        apiRest service = retrofit.create(apiRest.class);
        Call<ApiResponse> call = service.editToken(id,key,token,name);
        call.enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(Call<ApiResponse> call, Response<ApiResponse> response) {
                // Dismiss before leaving: dismissing a dialog after finish() throws
                // "View not attached to window manager".
                dismissProgress();
                if (response.isSuccessful()){
                    prf.setString("NAME_USER",name );
                    final ApiResponse body = response.body();
                    if (body != null && body.getMessage() != null) {
                        Toasty.success(getApplicationContext(), body.getMessage(),
                                Toast.LENGTH_SHORT, true).show();
                    }
                    Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
                    startActivity(intent);
                    finish();
                }
            }
            @Override
            public void onFailure(Call<ApiResponse> call, Throwable t) {
                dismissProgress();
                Toasty.error(getApplicationContext(), "Operation has been cancelled!", Toast.LENGTH_SHORT, true).show();
            }
        });
    }

    /** Closes the progress dialog if one is up and this screen is still there. */
    private void dismissProgress() {
        if (register_progress != null && register_progress.isShowing() && !isFinishing()) {
            register_progress.dismiss();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            super.onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
