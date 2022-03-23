import Box from "@mui/material/Box";
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import React from "react";
import {TableFees} from "./TableFees";
import Container from '@mui/material/Container';
import Grid from '@mui/material/Grid';
import useMediaQuery from '@mui/material/useMediaQuery';
import {useTheme} from '@mui/material/styles';

export function OneTableTabs(props) {
  const {fees} = props;
  const [value, setValue] = React.useState(0);
  const theme = useTheme();

  const handleChange = (event, newValue) => {
    setValue(newValue);
  };

  const fitCons = useMediaQuery(theme.breakpoints.up("417"));

  function TabPanel(props) {
    const {children, value, index, ...other} = props;

    return (
      <div
        role="tabpanel"
        hidden={value !== index}
        id={`simple-tabpanel-${index}`}
        aria-labelledby={`simple-tab-${index}`}
        {...other}
      >
        {value === index && (
          <Box sx={{p: 3}}>
            {children}
          </Box>
        )}
      </div>
    );
  }

  return (
    <Container sx={{mt: 1}}>
      <Box sx={{borderBottom: 1, borderColor: 'divider'}}>
        <Tabs centered value={value} onChange={handleChange} aria-label="basic tabs example">
          {fitCons && <Tab label="Conservative" />}
          {!fitCons && <Tab label="Conserv." />}
          <Tab label="Normal" />
          {fitCons && <Tab label="Economic" />}
          {!fitCons && <Tab label="Econ." />}
        </Tabs>
      </Box>
      <TabPanel value={value} index={0}>
        <Grid container justifyContent="center">
          <Grid item>
            <TableFees feeList={fees.csfl} estimationType="Conservative" header={false} />
          </Grid>
        </Grid>
      </TabPanel>
      <TabPanel value={value} index={1}>
        <Grid container justifyContent="center">
          <Grid item>
            <TableFees feeList={fees.nsfl} estimationType="Normal" header={false} />
          </Grid>
        </Grid>
      </TabPanel>
      <TabPanel value={value} index={2}>
        <Grid container justifyContent="center">
          <Grid item>
            <TableFees feeList={fees.esfl} estimationType="Economic" header={false} />
          </Grid>
        </Grid>
      </TabPanel>
    </Container>
  );
}
