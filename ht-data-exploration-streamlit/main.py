import streamlit as st

pg = st.navigation(
    [st.Page("data.py", title="Data Exploration", icon=":material/add_circle:"),
     st.Page("plots.py", title="Summary Statistics on the Data"),
     st.Page("machine_readable_tests.py", title="Using the data in one file"),
    #  st.Page("Interactive_Data_Explorer.py"),
    #  st.Page("Sidebar_Theming.py"),
     ]
     )

pg.run()

