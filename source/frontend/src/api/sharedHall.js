import request from '../utils/request'

export const listSharedHallPackages = (params) => request.get('/shared-hall/packages', { params })
export const submitSharedHallPackage = (payload) => request.post('/shared-hall/packages', payload)
