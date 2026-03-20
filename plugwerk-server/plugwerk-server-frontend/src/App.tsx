import { Container, Typography, AppBar, Toolbar } from '@mui/material'
import ExtensionIcon from '@mui/icons-material/Extension'

function App() {
  return (
    <>
      <AppBar position="static">
        <Toolbar>
          <ExtensionIcon sx={{ mr: 1 }} />
          <Typography variant="h6" component="div">
            Plugwerk
          </Typography>
        </Toolbar>
      </AppBar>
      <Container maxWidth="lg" sx={{ mt: 4 }}>
        <Typography variant="h4" gutterBottom>
          Plugin Marketplace
        </Typography>
        <Typography color="text.secondary">
          Plugwerk server is running. Full UI will be implemented in Milestone 6.
        </Typography>
      </Container>
    </>
  )
}

export default App
