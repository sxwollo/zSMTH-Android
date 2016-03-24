package com.zfdang.zsmth_android;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.zfdang.SMTHApplication;
import com.zfdang.zsmth_android.models.GuidanceContent;
import com.zfdang.zsmth_android.models.Topic;
import com.zfdang.zsmth_android.newsmth.SMTHHelper;

import okhttp3.ResponseBody;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;


/**
 * A fragment representing a list of Items.
 * <p/>
 * Activities containing this fragment MUST implement the {@link OnListFragmentInteractionListener}
 * interface.
 */
public class GuidanceFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener{

    private final String TAG = "Guidance Fragment";

    private static final String ARG_COLUMN_COUNT = "column-count";
    private int mColumnCount = 1;
    private OnListFragmentInteractionListener mListener;

    private RecyclerView mRecyclerView = null;
    private SwipeRefreshLayout mSwipeRefreshLayout = null;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public GuidanceFragment() {
    }

    @SuppressWarnings("unused")
    public static GuidanceFragment newInstance(int columnCount) {
        GuidanceFragment fragment = new GuidanceFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_COLUMN_COUNT, columnCount);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mColumnCount = getArguments().getInt(ARG_COLUMN_COUNT);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_guidance, container, false);

        // http://sapandiwakar.in/pull-to-refresh-for-android-recyclerview-or-any-other-vertically-scrolling-view/
        // pull to refresh for android recyclerview
        mSwipeRefreshLayout = (SwipeRefreshLayout) rootView;
        mSwipeRefreshLayout.setOnRefreshListener(this);

        // http://blog.csdn.net/lmj623565791/article/details/45059587
        // 你想要控制Item间的间隔（可绘制），请通过ItemDecoration
        // 你想要控制Item增删的动画，请通过ItemAnimator
        // 你想要控制点击、长按事件，请自己写
        // item被按下的时候的highlight,这个是通过guidance item的backgroun属性来实现的 (android:background="@drawable/recyclerview_item_bg")
        mRecyclerView = (RecyclerView) rootView.findViewById(R.id.guidance_recycler_view);
        // Set the adapter
        if (mRecyclerView != null) {
            mRecyclerView.addItemDecoration(new DividerItemDecoration(getActivity(), LinearLayoutManager.VERTICAL));
            Context context = rootView.getContext();
            if (mColumnCount <= 1) {
                mRecyclerView.setLayoutManager(new LinearLayoutManager(context));
            } else {
                mRecyclerView.setLayoutManager(new GridLayoutManager(context, mColumnCount));
            }
            mRecyclerView.setItemAnimator(new DefaultItemAnimator());
            mRecyclerView.setAdapter(new GuidanceRecyclerViewAdapter(GuidanceContent.TOPICS, mListener));
        }

        getActivity().setTitle(SMTHApplication.App_Title_Prefix + "首页导读");

        if(GuidanceContent.TOPICS.size() == 0){
            // only refresh guidance when there is no topic available
            MainActivity activity = (MainActivity)getActivity();
            activity.showProgress("获取导读信息...", true);
            RefreshGuidance();
        }
        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onRefresh() {
        // triggered by SwipeRefreshLayout
        // setRefreshing(false) should be called later
        RefreshGuidance();
    }

    public void RefreshGuidance() {
        RefreshGuidanceFromMobile();
    }


    final String[] SectionName = {"十大", "推荐", "国内院校", "休闲娱乐", "五湖四海", "游戏运动", "社会信息", "知性感性", "文化人文", "学术科学", "电脑技术"};
    final String[] SectionURLPath = {"topTen", "recommend", "1", "2", "3", "4", "5", "6", "7", "8", "9"};

    public void RefreshGuidanceFromMobile() {
        final SMTHHelper helper = SMTHHelper.getInstance();

        Observable.from(SectionURLPath)
                .concatMap(new Func1<String, Observable<ResponseBody>>() {
                    @Override
                    public Observable<ResponseBody> call(String sectionURL) {
                        return helper.mService.hotTopicsBySection(sectionURL);
                    }
                })
                .concatMap(new Func1<ResponseBody, Observable<Topic>>() {
                    @Override
                    public Observable<Topic> call(ResponseBody responseBody) {
                        try {
                            String resp = responseBody.string();
                            return Observable.from(SMTHHelper.ParseHotTopicsFromMobile(resp));
                        } catch (Exception e) {
                            Log.d(TAG, e.toString());
                            Log.d(TAG, "获取热帖失败!");
                            return null;
                        }
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<Topic>() {
                    @Override
                    public void onStart() {
                        super.onStart();

                        // clear current hot topics
                        GuidanceContent.clear();
                        mRecyclerView.getAdapter().notifyDataSetChanged();
                    }

                    @Override
                    public void onCompleted() {
                        Topic topic = new Topic("-- END --");
                        GuidanceContent.addItem(topic);
                        mRecyclerView.getAdapter().notifyItemInserted(GuidanceContent.TOPICS.size() - 1);

                        clearLoadingHints();

                        // show finish toast
                        Toast.makeText(getActivity(), "刷新完成!", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.d(TAG, e.toString());
                        clearLoadingHints();

                        Toast.makeText(getActivity(), "获取热帖失败", Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onNext(Topic topic) {
                        Log.d(TAG, topic.toString());
                        GuidanceContent.addItem(topic);
                        mRecyclerView.getAdapter().notifyItemInserted(GuidanceContent.TOPICS.size() - 1);
                    }
                });


    }


    public void clearLoadingHints () {
        // disable progress bar
        MainActivity activity = (MainActivity) getActivity();
        activity.showProgress("", false);

        // disable SwipeFreshLayout
        mSwipeRefreshLayout.setRefreshing(false);
    }


    // http://stackoverflow.com/questions/32604552/onattach-not-called-in-fragment
    // If you run your application on a device with API 23 (marshmallow) then onAttach(Context) will be called.
    // On all previous Android Versions onAttach(Activity) will be called.
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnListFragmentInteractionListener) {
            mListener = (OnListFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnListFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }


    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p/>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnListFragmentInteractionListener {
        // TODO: Update argument type and name
        void onListFragmentInteraction(Topic item);
    }
}
