import request from '../utils/request'

export const login = (payload) => request.post('/auth/login', payload)
export const me = () => request.get('/auth/me')
export const bootstrapAdmin = (payload) => request.post('/auth/bootstrap-admin', payload)
export const bootstrapStatus = () => request.get('/auth/bootstrap-status')
