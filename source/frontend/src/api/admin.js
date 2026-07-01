import request from '../utils/request'

export const listUsers = (params) => request.get('/admin/users', { params })
export const createUser = (payload) => request.post('/admin/users', payload)
export const deleteUser = (id) => request.delete(`/admin/users/${id}`)

export const listGenerals = (params) => request.get('/admin/generals', { params })
export const createGeneral = (formData) => request.post('/admin/generals', formData)
export const updateGeneral = (id, formData) => request.put(`/admin/generals/${id}`, formData)
export const deleteGeneral = (id) => request.delete(`/admin/generals/${id}`)

export const listIdentityModes = () => request.get('/admin/identity-modes')
export const createIdentityMode = (payload) => request.post('/admin/identity-modes', payload)
export const updateIdentityMode = (id, payload) => request.put(`/admin/identity-modes/${id}`, payload)
export const deleteIdentityMode = (id) => request.delete(`/admin/identity-modes/${id}`)

export const listRooms = (params) => request.get('/admin/rooms', { params })
export const dissolveRoom = (roomCode) => request.delete(`/admin/rooms/${roomCode}`)

export const getSettings = () => request.get('/admin/settings')
export const updateSettings = (payload) => request.put('/admin/settings', payload)
